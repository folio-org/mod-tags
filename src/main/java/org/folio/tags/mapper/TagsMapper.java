package org.folio.tags.mapper;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.springframework.data.domain.Page;

import org.folio.tags.dao.model.Tag;
import org.folio.tags.domain.dto.Metadata;
import org.folio.tags.domain.dto.TagDto;
import org.folio.tags.domain.dto.TagDtoCollection;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TagsMapper {

  @Mapping(target = "metadata", expression = "java(toMetadata(entity))")
  TagDto toDto(Tag entity);

  @InheritInverseConfiguration
  Tag toEntity(TagDto dto);

  default TagDtoCollection toDtoCollection(Page<Tag> entityList) {
    return new TagDtoCollection().tags(toDtoList(entityList.getContent())).totalRecords(
      Math.toIntExact(entityList.getTotalElements()));
  }

  List<TagDto> toDtoList(List<Tag> entityList);

  @Mapping(target = "updatedDate", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "createdDate", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  Tag updateTag(TagDto dto, @MappingTarget Tag entity);

  @Mapping(target = "updatedByUserId", source = "updatedBy")
  @Mapping(target = "createdByUserId", source = "createdBy")
  Metadata toMetadata(Tag entity);

  default OffsetDateTime map(Timestamp value) {
    return value != null ? OffsetDateTime.from(value.toInstant().atZone(ZoneId.systemDefault())) : null;
  }

  default String map(UUID value) {
    return value != null ? value.toString() : null;
  }

  default UUID map(String value) {
    return (StringUtils.isBlank(value)) ? null : UUID.fromString(value);
  }
}
