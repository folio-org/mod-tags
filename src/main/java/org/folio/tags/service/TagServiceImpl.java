package org.folio.tags.service;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.data.OffsetRequest;
import org.folio.tags.dao.model.Tag;
import org.folio.tags.dao.repository.TagRepository;
import org.folio.tags.domain.dto.TagDto;
import org.folio.tags.domain.dto.TagDtoCollection;
import org.folio.tags.mapper.TagsMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

  private final TagRepository repository;
  private final TagsMapper mapper;

  @Override
  public TagDtoCollection fetchTagCollection(String query, Integer offset, Integer limit) {
    log.debug("fetchTagCollection:: trying to fetch tag collection by query: {}, offset: {}, limit: {}", query, offset, limit);
    Page<Tag> tagPage = repository.findByCQL(query, OffsetRequest.of(offset, limit));
    log.info("fetchTagCollection:: loaded tags: {}", tagPage.getNumberOfElements());
    return mapper.toDtoCollection(tagPage);
  }

  @Override
  public TagDto createTag(TagDto tag) {
    log.debug("createTag:: trying to create a tag with: {}", tag);
    Tag saved = repository.save(mapper.toEntity(tag));
    log.info("createTag:: created a tag: {}", saved);
    return mapper.toDto(saved);
  }

  @Override
  public TagDto fetchTagById(UUID id) {
    log.debug("fetchTagById:: trying to fetch a tag with id: {}", id);
    Optional<Tag> byId = repository.findById(id);
    byId.ifPresent((b) -> {
      log.info("fetchTagById:: loaded tag with id: {}", b.getId());
    });
    return byId
      .map(mapper::toDto)
      .orElseThrow(() -> notFound(id));
  }

  @Override
  public void updateTag(UUID id, TagDto tag) {
    log.debug("updateTag:: trying to update a tag with id: {}", id);
    repository.findById(id)
      .ifPresentOrElse(existedTag -> repository.save(mapper.updateTag(tag, existedTag)), throwNotFoundById(id));
    log.info("updateTag:: updated a tag with id: {}", id);
  }

  @Override
  public void removeTagById(UUID id) {
    log.debug("removeTagById:: trying to remove a tag with id: {}", id);
    repository.findById(id)
      .ifPresentOrElse((i) -> {
        repository.delete(i);
        log.debug("removeTagById:: removed a tag with id: {}", id);
      }, throwNotFoundById(id));
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
