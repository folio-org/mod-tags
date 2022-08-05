package org.folio.tags.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.tags.domain.dto.TagDto;
import org.folio.tags.domain.dto.TagDtoCollection;
import org.folio.tags.rest.resource.TagsApi;
import org.folio.tags.service.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TagsController implements TagsApi {

  private final TagService tagService;

  @Override
  public ResponseEntity<Void> deleteTagById(String id) {
    tagService.removeTagById(UUID.fromString(id));
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<TagDto> getTagById(String id) {
    return ResponseEntity.ok(tagService.fetchTagById(UUID.fromString(id)));
  }

  @Override
  public ResponseEntity<TagDtoCollection> getTagCollection(String query, Integer offset, Integer limit) {
    return ResponseEntity.ok(tagService.fetchTagCollection(query, offset, limit));
  }

  @Override
  public ResponseEntity<TagDto> postTag(TagDto tag) {
    TagDto newTag = tagService.createTag(tag);
    return ResponseEntity.created(URI.create("/tags/" + newTag.getId())).body(newTag);
  }

  @Override
  public ResponseEntity<Void> putTagById(String id, TagDto tag) {
    tagService.updateTag(UUID.fromString(id), tag);
    return ResponseEntity.noContent().build();
  }
}
