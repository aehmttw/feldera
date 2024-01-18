from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.service_config_type_0 import ServiceConfigType0


T = TypeVar("T", bound="UpdateServiceRequest")


@define
class UpdateServiceRequest:
    """Request to update an existing service.

    Attributes:
        config (Union['ServiceConfigType0', None, Unset]): Service configuration for the API

            A Service is an API object, with as one of its properties its config.
            The config is a variant of this enumeration, and is stored serialized
            in the database.

            How a service configuration is applied can vary by connector, e.g., some
            might have options that are mutually exclusive whereas others might be
            defaults that can be overriden.
        description (Union[Unset, None, str]): New service description. If absent, existing name will be kept
            unmodified.
        name (Union[Unset, None, str]): New service name. If absent, existing name will be kept unmodified.
    """

    config: Union["ServiceConfigType0", None, Unset] = UNSET
    description: Union[Unset, None, str] = UNSET
    name: Union[Unset, None, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        config: Union[Dict[str, Any], None, Unset]
        if isinstance(self.config, Unset):
            config = UNSET
        elif self.config is None:
            config = None

        else:
            config = self.config.to_dict()

        description = self.description
        name = self.name

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if config is not UNSET:
            field_dict["config"] = config
        if description is not UNSET:
            field_dict["description"] = description
        if name is not UNSET:
            field_dict["name"] = name

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.service_config_type_0 import ServiceConfigType0

        d = src_dict.copy()

        def _parse_config(data: object) -> Union["ServiceConfigType0", None, Unset]:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            if not isinstance(data, dict):
                raise TypeError()
            componentsschemas_service_config_type_0 = ServiceConfigType0.from_dict(data)

            return componentsschemas_service_config_type_0

        config = _parse_config(d.pop("config", UNSET))

        description = d.pop("description", UNSET)

        name = d.pop("name", UNSET)

        update_service_request = cls(
            config=config,
            description=description,
            name=name,
        )

        update_service_request.additional_properties = d
        return update_service_request

    @property
    def additional_keys(self) -> List[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> Any:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
