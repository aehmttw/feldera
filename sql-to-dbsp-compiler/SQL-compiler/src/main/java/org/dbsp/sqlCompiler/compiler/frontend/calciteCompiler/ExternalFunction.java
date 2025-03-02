package org.dbsp.sqlCompiler.compiler.frontend.calciteCompiler;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlCallBinding;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperandCountRange;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlOperandCountRanges;
import org.apache.calcite.sql.type.SqlOperandTypeChecker;
import org.apache.calcite.sql.type.SqlOperandTypeInference;
import org.apache.calcite.sql.type.SqlSingleOperandTypeChecker;
import org.apache.calcite.sql.type.SqlTypeName;
import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.errors.CompilationError;
import org.dbsp.sqlCompiler.compiler.errors.InternalCompilerError;
import org.dbsp.sqlCompiler.compiler.errors.SourcePositionRange;
import org.dbsp.sqlCompiler.compiler.errors.UnimplementedException;
import org.dbsp.sqlCompiler.compiler.frontend.ExpressionCompiler;
import org.dbsp.sqlCompiler.compiler.frontend.TypeCompiler;
import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.ir.DBSPFunction;
import org.dbsp.sqlCompiler.ir.DBSPParameter;
import org.dbsp.sqlCompiler.ir.expression.DBSPApplyExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPApplyMethodExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPBlockExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPVariablePath;
import org.dbsp.sqlCompiler.ir.statement.DBSPLetStatement;
import org.dbsp.sqlCompiler.ir.statement.DBSPStatement;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeAny;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeCode;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeUser;
import org.dbsp.util.Linq;
import org.dbsp.util.Utilities;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/** Represents a function that is implemented by the user in Rust */
public class ExternalFunction extends SqlFunction {
    /**
     * A variant ot OperandTypes.TypeNameChecker which does not extend the interface
     * ImplicitCastOperandTypeChecker.  We do not want to allow implicit casts for these operands.
     */
    private record ExactTypeNameChecker(SqlTypeName typeName) implements SqlSingleOperandTypeChecker {
        private ExactTypeNameChecker(SqlTypeName typeName) {
            this.typeName = requireNonNull(typeName, "typeName");
        }
         @Override
        public boolean checkSingleOperandType(SqlCallBinding callBinding,
                                              SqlNode operand, int iFormalOperand, boolean throwOnFailure) {
            final RelDataType operandType =
                    callBinding.getValidator().getValidatedNodeType(operand);
            return operandType.getSqlTypeName() == typeName;
        }
         @Override
        public String getAllowedSignatures(SqlOperator op, String opName) {
            return opName + "(" + typeName.getSpaceName() + ")";
        }
    }

    public final CalciteObject node;
    public final RelDataType returnType;
    public final List<RelDataTypeField> parameterList;
    /** True if the function is generated by the compiler */
    public final boolean generated;
    public final SourcePositionRange position;
    /** Cached return type */
    @Nullable
    public DBSPType structReturnType = null;
    @Nullable
    public RexNode body;

    static SqlOperandTypeChecker createTypeChecker(String function, List<RelDataTypeField> parameters) {
        if (parameters.isEmpty())
            return OperandTypes.NILADIC;
        SqlSingleOperandTypeChecker[] checkers = new SqlSingleOperandTypeChecker[parameters.size()];
        StringBuilder builder = new StringBuilder();
        builder.append(function).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            RelDataTypeField field = parameters.get(i);
            SqlTypeName typeName = field.getType().getSqlTypeName();
            // This type checker is a bit too strict.
            checkers[i] = new ExactTypeNameChecker(typeName);
            // This type checker allows implicit casts from any type in the type family,
            // which is not great.
            // checkers[i] = OperandTypes.typeName(typeName);
            if (i > 0)
                builder.append(", ");
            builder.append("<")
                    .append(field.getType().toString())
                    .append(">");
        }
        builder.append(")");
        if (checkers.length > 1)
            return OperandTypes.sequence(builder.toString(), checkers);
        return checkers[0];
    }

    static SqlOperandTypeInference createTypeinference(List<RelDataTypeField> parameters) {
        return InferTypes.explicit(Linq.map(parameters, RelDataTypeField::getType));
    }

    public ExternalFunction(SqlIdentifier name, RelDataType returnType,
                            List<RelDataTypeField> parameters, @Nullable RexNode body, boolean generated) {
        super(name.getSimple(), SqlKind.OTHER_FUNCTION, ReturnTypes.explicit(returnType),
                createTypeinference(parameters), createTypeChecker(name.getSimple(), parameters),
                SqlFunctionCategory.USER_DEFINED_FUNCTION);
        this.node = CalciteObject.create(name);
        this.position = new SourcePositionRange(name.getParserPosition());
        this.body = body;
        this.returnType = returnType;
        this.parameterList = parameters;
        this.generated = generated;
    }

    @Override
    public boolean isDeterministic() {
        return false;
    }

    @Override
    public SqlOperandCountRange getOperandCountRange() {
        return SqlOperandCountRanges.of(this.parameterList.size());
    }

    /** Return type as declared in the SQL program, not as implemented.
     * I.e., may be a struct. */
    public DBSPType getFunctionReturnType(TypeCompiler compiler) {
        if (this.structReturnType == null)
            this.structReturnType = compiler.convertType(this.returnType, true);
        return this.structReturnType;
    }

    /** Visitor used to generate the function body from a RexNode */
    static class FunctionBodyGenerator extends ExpressionCompiler {
        final List<DBSPParameter> parameters;

        /* Customize the input ref to refer to parameters instead */
        @Override
        public DBSPExpression visitInputRef(RexInputRef inputRef) {
            CalciteObject node = CalciteObject.create(inputRef);
            // +1 because the first parameter is the source position parameter
            int index = inputRef.getIndex() + 1;
            if (index < this.parameters.size()) {
                return this.parameters.get(index).asVariable();
            }
            throw new InternalCompilerError("Parameter index out of bounds ", node);
        }

        public FunctionBodyGenerator(DBSPCompiler compiler, List<DBSPParameter> parameters) {
            super(null, compiler);
            this.parameters = parameters;
        }
    }

    static DBSPParameter sourcePositionParameter() {
        return new DBSPParameter("_pos",
                new DBSPTypeUser(CalciteObject.EMPTY, DBSPTypeCode.USER, "SourcePositionRange", false)
                        .ref());
    }

    @Nullable
    public DBSPFunction getImplementation(TypeCompiler typeCompiler, DBSPCompiler compiler) {
        if (this.body != null) {
            List<DBSPParameter> parameters = new ArrayList<>();
            // First paramter is always source position
            parameters.add(sourcePositionParameter());
            for (RelDataTypeField param: this.parameterList) {
                DBSPType type = typeCompiler.convertType(param.getType(), true);
                DBSPParameter p = new DBSPParameter(param.getName(), type);
                parameters.add(p);
            }
            FunctionBodyGenerator generator = new FunctionBodyGenerator(compiler, parameters);
            DBSPType returnType = typeCompiler.convertType(this.returnType, true);
            DBSPExpression functionBody = generator.compile(this.body);
            if (!returnType.sameType(functionBody.getType())) {
                throw new CompilationError("Function " + Utilities.singleQuote(this.getName()) +
                        " should return " + Utilities.singleQuote(this.returnType.getFullTypeString()) +
                        " but returns " + Utilities.singleQuote(this.body.getType().getFullTypeString()), this.node);
            }
            return new DBSPFunction(this.getName(), parameters, returnType, functionBody, Linq.list());
        }
        if (!this.generated)
            return null;
        // Some classes of functions are automatically generated by the compiler.
        if (this.getName().toLowerCase().startsWith("jsonstring_as_")) {
            /*
            TYPE ~ struct
            pub fn JSONSTRING_AS_<TYPE>(_pos: &SourcePositionRange, s: Option<String>) -> Option<Tup1<TYPE>> {
                let s: String = (s?);
                let json = try_parse_json(s)?;
                let strct: Option<struct_0> = json_as(json);
                strct.map(move |x: _, | -> _ { x.into() })
            }
             */
            DBSPType returnType = typeCompiler.convertType(this.returnType, false);
            DBSPType structType = this.getFunctionReturnType(typeCompiler);
            if (this.parameterList.size() != 1) {
                compiler.reportError(this.position, "Incorrect signature",
                        "Function " + Utilities.singleQuote(this.getName()) +
                                " must have a single parameter");
                return null;
            }
            DBSPType parameterType = typeCompiler.convertType(this.parameterList.get(0).getType(), false);
            DBSPParameter position = sourcePositionParameter();
            DBSPParameter param = new DBSPParameter("s", parameterType);
            List<DBSPStatement> statements = new ArrayList<>();
            if (parameterType.mayBeNull)
                statements.add(new DBSPLetStatement("s", param.asVariable().question()));
            DBSPLetStatement json = new DBSPLetStatement("json",
                    new DBSPApplyExpression("try_parse_json", DBSPTypeAny.getDefault(), param.asVariable())
                            .question());
            statements.add(json);
            DBSPExpression toStruct = new DBSPApplyExpression("try_json_as", structType.setMayBeNull(true), json.getVarReference());
            DBSPLetStatement strct = new DBSPLetStatement("strct", toStruct);
            statements.add(strct);
            DBSPVariablePath var = new DBSPVariablePath("x", DBSPTypeAny.getDefault());
            DBSPExpression into = new DBSPApplyMethodExpression("into", var.getType(), var).closure(var.asParameter());
            DBSPExpression retval = new DBSPApplyMethodExpression("map", returnType, strct.getVarReference(), into);
            DBSPExpression body = new DBSPBlockExpression(statements, retval);
            // DBSPExpression ok = new DBSPApplyMethodExpression("Ok", new DBSPTypeResult(returnType), retval);
            // TODO: the function should return Result
            return new DBSPFunction(this.getName(), Linq.list(position, param), returnType, body, Linq.list());
        }
        throw new UnimplementedException();
    }
}
