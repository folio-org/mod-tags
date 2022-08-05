package org.folio.tags.service;

import java.util.UUID;
import javax.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.folio.spring.data.OffsetRequest;
import org.folio.tags.dao.repository.TagRepository;
import org.folio.tags.domain.dto.TagDto;
import org.folio.tags.domain.dto.TagDtoCollection;
import org.folio.tags.mapper.TagsMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

  private final TagRepository repository;
  private final TagsMapper mapper;

  @Override
  public TagDtoCollection fetchTagCollection(String query, Integer offset, Integer limit) {
    return mapper.toDtoCollection(repository.findByCQL(query, OffsetRequest.of(offset, limit)));
  }

  @Override
  public TagDto createTag(TagDto tag) {
    return mapper.toDto(repository.save(mapper.toEntity(tag)));
  }

  @Override
  public TagDto fetchTagById(UUID id) {
    return repository.findById(id)
      .map(mapper::toDto)
      .orElseThrow(() -> notFound(id));
  }

  @Override
  public void updateTag(UUID id, TagDto tag) {
    repository.findById(id)
      .ifPresentOrElse(existedTag -> repository.save(mapper.updateTag(tag, existedTag)), throwNotFoundById(id));
  }

  @Override
  public void removeTagById(UUID id) {
    repository.findById(id)
      .ifPresentOrElse(repository::delete, throwNotFoundById(id));
  }

  private Runnable throwNotFoundById(UUID id) {
    return () -> {
      throw notFound(id);
    };
  }

  private EntityNotFoundException notFound(UUID id) {
    return new EntityNotFoundException(String.format("Tag with id [%s] was not found", id));
  }
}
