package org.folio.tags.service;

import java.util.UUID;

import org.folio.tags.domain.dto.TagDto;
import org.folio.tags.domain.dto.TagDtoCollection;

public interface TagService {

  TagDtoCollection fetchTagCollection(String query, Integer offset, Integer limit);

  TagDto createTag(TagDto tag);

  TagDto fetchTagById(UUID id);

  void updateTag(UUID id, TagDto tag);

  void removeTagById(UUID id);
}
