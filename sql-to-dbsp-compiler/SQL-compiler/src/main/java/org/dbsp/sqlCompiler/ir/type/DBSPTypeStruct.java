/*
 * Copyright 2022 VMware, Inc.
 * SPDX-License-Identifier: MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.dbsp.sqlCompiler.ir.type;

import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.compiler.visitors.inner.InnerVisitor;
import org.dbsp.sqlCompiler.ir.DBSPNode;
import org.dbsp.sqlCompiler.ir.IDBSPInnerNode;
import org.dbsp.sqlCompiler.ir.IDBSPNode;
import org.dbsp.util.IIndentStream;
import org.dbsp.util.Linq;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static org.dbsp.sqlCompiler.ir.type.DBSPTypeCode.STRUCT;

public class DBSPTypeStruct extends DBSPType {
    public static class Field extends DBSPNode implements IHasType, IDBSPInnerNode {
        /** Position within struct */
        public final int index;
        public final String name;
        public final boolean nameIsQuoted;
        public final DBSPType type;

        public Field(CalciteObject node, String name, int index,
                     DBSPType type, boolean nameIsQuoted) {
            super(node);
            this.name = name;
            this.index = index;
            this.type = type;
            this.nameIsQuoted = nameIsQuoted;
        }

        public String getName() {
            return this.name;
        }

        public String getSanitizedName() {
            return "field" + this.index;
        }

        public DBSPType getType() {
            return this.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.name, this.type.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Field that = (Field) o;
            return this.name.equals(that.name) &&
                    Objects.equals(this.index, that.index) &&
                    this.type.sameType(that.type);
        }

        @Override
        public void accept(InnerVisitor visitor) {
            VisitDecision decision = visitor.preorder(this);
            if (decision.stop()) return;
            visitor.push(this);
            this.type.accept(visitor);
            visitor.pop(this);
            visitor.postorder(this);
        }

        @Override
        public boolean sameFields(IDBSPNode other) {
            Field o = other.as(Field.class);
            if (o == null)
                return false;
            return this.name.equals(o.name) &&
                    Objects.equals(this.index, o.index) &&
                    this.type.sameType(o.type);
        }

        @Override
        public IIndentStream toString(IIndentStream builder) {
            return builder
                    .append(this.name)
                    .append(": ")
                    .append(this.type);
        }
    }

    public final String name;
    public final String sanitizedName;
    public final LinkedHashMap<String, Field> fields;

    public DBSPTypeStruct(CalciteObject node, String name, String sanitizedName,
                          Collection<Field> args, boolean mayBeNull) {
        super(node, STRUCT, mayBeNull);
        this.sanitizedName = sanitizedName;
        this.name = name;
        this.fields = new LinkedHashMap<>();
        for (Field f: args) {
            if (this.hasField(f.getName()))
                this.error("Field name " + f + " is duplicated");
            this.fields.put(f.name, f);
        }
    }

    public DBSPTypeStruct rename(String newName) {
        return new DBSPTypeStruct(this.getNode(), newName, this.sanitizedName, this.fields.values(), this.mayBeNull);
    }

    @Override
    public DBSPType setMayBeNull(boolean mayBeNull) {
        if (this.mayBeNull == mayBeNull)
            return this;
        return new DBSPTypeStruct(this.getNode(), this.name, this.sanitizedName, this.fields.values(), mayBeNull);
    }

    public boolean hasField(String fieldName) {
        return this.fields.containsKey(fieldName);
    }

    @Override
    public boolean sameType(DBSPType type) {
        if (!super.sameNullability(type))
            return false;
        if (!type.is(DBSPTypeStruct.class))
            return false;
        DBSPTypeStruct other = type.to(DBSPTypeStruct.class);
        if (!this.name.equals(other.name))
            return false;
        if (!Objects.equals(this.sanitizedName, other.sanitizedName))
            return false;
        if (this.fields.size() != other.fields.size())
            return false;
        for (String name: this.fields.keySet()) {
            Field otherField = other.getField(name);
            if (otherField == null)
                return false;
            Field field = Objects.requireNonNull(this.getField(name));
            if (!field.equals(otherField))
                return false;
        }
        return true;
    }

    @Nullable
    public Field getField(String name) {
        return this.fields.get(name);
    }

    public Field getExistingField(String name) {
        return Objects.requireNonNull(this.fields.get(name));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.name, this.sanitizedName, this.fields.hashCode());
    }

    public DBSPType getFieldType(String fieldName) {
        Field field = this.getExistingField(fieldName);
        return field.type;
    }

    @Override
    public void accept(InnerVisitor visitor) {
        VisitDecision decision = visitor.preorder(this);
        if (decision.stop()) return;
        visitor.push(this);
        for (Field f: this.fields.values())
            f.accept(visitor);
        visitor.pop(this);
        visitor.postorder(this);
    }

    @Override
    public IIndentStream toString(IIndentStream builder) {
        return builder.append("struct ")
                .append(this.mayBeNull ? "?" : "")
                .append(this.name);
    }

    /** Generate a tuple type by ignoring the struct and field names. */
    public DBSPTypeTuple toTuple() {
        List<DBSPType> types = Linq.list(Linq.map(this.fields.values(), f -> f.type));
        return new DBSPTypeTuple(this.getNode(), types);
    }

    private static DBSPType toTupleDeep(DBSPType type) {
        if (type.is(DBSPTypeStruct.class)) {
            DBSPTypeStruct struct = type.to(DBSPTypeStruct.class);
            List<DBSPType> types = Linq.list(Linq.map(struct.fields.values(), f -> toTupleDeep(f.type)));
            return new DBSPTypeTuple(struct.getNode(), types);
        } else if (type.is(DBSPTypeUser.class)) {
            DBSPTypeUser user = type.to(DBSPTypeUser.class);
            DBSPType[] args = Linq.map(user.typeArgs, DBSPTypeStruct::toTupleDeep, DBSPType.class);
            return new DBSPTypeUser(user.getNode(), user.code, user.name, user.mayBeNull, args);
        } else if (type.is(DBSPTypeTupleBase.class)) {
            DBSPTypeTupleBase tuple = type.to(DBSPTypeTupleBase.class);
            DBSPType[] fields = Linq.map(tuple.tupFields, DBSPTypeStruct::toTupleDeep, DBSPType.class);
            return tuple.makeType(Linq.list(fields));
        }
        return type;
    }

    /** Generate a tuple type by ignoring the struct and field names, recursively. */
    public DBSPType toTupleDeep() {
        return toTupleDeep(this);
    }
}
