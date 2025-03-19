package org.folio.tags.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.folio.spring.cql.CqlQueryValidationException;
import org.folio.tags.dao.model.Tag;
import org.folio.tags.domain.dto.TagDto;
import org.folio.tags.support.ApiTest;
import org.folio.tags.util.ErrorsHelper;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TagsApiTest extends ApiTest {

  private static final String TAGS_LOCATION_PATTERN =
    "^/tags/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

  @BeforeEach
  void setUp() {
    databaseHelper.clearTable(TENANT);
  }

  @Test
  @DisplayName("Check health")
  void checkHealth() throws Exception {
    mockMvc.perform(get("/admin/health").headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("UP")));
  }

  // Tests for GET /tags

  @Test
  @DisplayName("Find all tags - empty collection")
  void returnEmptyTagCollection() throws Exception {
    mockMvc.perform(get("/tags").headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tags").doesNotHaveJsonPath())
      .andExpect(jsonPath("$.totalRecords").value(0));
  }

  @Test
  @DisplayName("Find all tags")
  void returnTagCollection() throws Exception {
    databaseHelper.saveTags(List.of(
      Tag.builder().label("important").build(),
      Tag.builder().label("urgent").build()
    ), TENANT);

    mockMvc.perform(get("/tags").headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(idMatch("$.tags.[0]", not(emptyOrNullString())))
      .andExpect(idMatch("$.tags.[1]", not(emptyOrNullString())))
      .andExpect(labelMatch("$.tags.[0]", is("important")))
      .andExpect(labelMatch("$.tags.[1]", is("urgent")))
      .andExpect(jsonPath("$.totalRecords").value(2));
  }

  @Test
  @DisplayName("Find all tags with sort by label and limited with offset")
  void returnTagCollectionSortedByLabelAndLimitedWithOffset() throws Exception {
    var tagList = List.of(
      Tag.builder().label("important").build(),
      Tag.builder().label("asap").build(),
      Tag.builder().label("urgent").build()
    );
    databaseHelper.saveTags(tagList, TENANT);

    var cqlQuery = "(cql.allRecords=1)sortby label/sort.descending";
    var limit = "1";
    var offset = "1";
    mockMvc.perform(get("/tags?limit={l}&offset={o}&query={cql}", limit, offset, cqlQuery)
        .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(labelMatch("$.tags.[0]", is("important")))
      .andExpect(jsonPath("$.tags.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(3));
  }

  @Test
  @DisplayName("Find all tags by label")
  void returnTagCollectionByLabel() throws Exception {
    var tagList = List.of(
      Tag.builder().label("important").build(),
      Tag.builder().label("asap").build(),
      Tag.builder().label("urgent").build()
    );
    databaseHelper.saveTags(tagList, TENANT);

    var cqlQuery = "label=asap";
    mockMvc.perform(get("/tags?query={cql}", cqlQuery)
        .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(labelMatch("$.tags.[0]", is("asap")))
      .andExpect(jsonPath("$.tags.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(1));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid CQL query")
  void return422OnGetCollectionWithInvalidCqlQuery() throws Exception {
    var cqlQuery = "!invalid-cql!";
    mockMvc.perform(get("/tags?query={cql}", cqlQuery)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(CqlQueryValidationException.class))
      .andExpect(errorMessageMatch(containsString("Not implemented yet node type")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid offset")
  void return422OnGetCollectionWithInvalidOffset() throws Exception {
    mockMvc.perform(get("/tags?offset={offset}", -1)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must be greater than or equal to 0")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid limit")
  void return422OnGetCollectionWithInvalidLimit() throws Exception {
    mockMvc.perform(get("/tags?limit={limit}", -1)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must be greater than or equal to 1")));
  }

  // Tests for POST /tags

  @Test
  @DisplayName("Create new tag")
  void createNewTag() throws Exception {
    var label = "First tag";
    var description = "This is the first test tag";
    var tag = new TagDto().label(label).description(description).id(UUID.randomUUID().toString());
    mockMvc.perform(postTag(tag))
      .andExpect(status().isCreated())
      .andExpect(header().string(HttpHeaders.LOCATION, matchesRegex(TAGS_LOCATION_PATTERN)))
      .andExpect(labelMatch("$", is(label)))
      .andExpect(descriptionMatch("$", is(description)))
      .andExpect(jsonPath("$.id").isNotEmpty())
      .andExpect(jsonPath("$.metadata.createdByUserId").value(USER_ID))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 422 on post tag with duplicate label")
  void return422OnPostWithDuplicateLabel() throws Exception {
    var label = "Tag";
    databaseHelper.saveTag(Tag.builder().id(UUID.randomUUID()).label(label).build(), TENANT);

    var duplicateTag = new TagDto().label(label);
    mockMvc.perform(postTag(duplicateTag))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(DataIntegrityViolationException.class))
      .andExpect(errorTypeMatch(ErrorsHelper.ErrorType.INTERNAL.getTypeCode()));
  }

  // Tests for GET /tags/{id}
  @Test
  @DisplayName("Find tag by ID")
  void returnTagById() throws Exception {
    var id = UUID.randomUUID();
    var label = "important";
    var tag = Tag.builder().id(id).label(label).build();
    databaseHelper.saveTag(tag, TENANT);

    mockMvc.perform(getTagById(id))
      .andExpect(status().isOk())
      .andExpect(idMatch("$", is(id.toString())))
      .andExpect(labelMatch("$", is(label)))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 404 on get tag by ID when it is not exist")
  void return404OnGetTagByIdWhenItNotExist() throws Exception {
    mockMvc.perform(getTagById(UUID.randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(EntityNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  @Test
  @DisplayName("Return 422 on get tag by ID when it is invalid")
  void return422OnGetTagByIdWhenIdIsInvalid() throws Exception {
    mockMvc.perform(getTagById("invalid-uuid"))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must match")));
  }

  // Tests for PUT /tags/{id}
  @Test
  @DisplayName("Update existing tag")
  void updateExistingTag() throws Exception {
    var id = UUID.randomUUID();
    var label = "important";
    var description = "This is the first test tag";
    var tag = Tag.builder().id(id).label(label).description(description).build();
    databaseHelper.saveTag(tag, TENANT);

    var updatedLabel = "updatedLabel";
    var updatedDescription = "updatedDescription";
    var updatedTag = new TagDto().id(id.toString()).label(updatedLabel).description(updatedDescription);
    mockMvc.perform(putTagById(id, updatedTag))
      .andExpect(status().isNoContent());

    mockMvc.perform(getTagById(id))
      .andExpect(idMatch("$", is(id.toString())))
      .andExpect(labelMatch("$", is(updatedLabel)))
      .andExpect(descriptionMatch("$", is(updatedDescription)))
      .andExpect(jsonPath("$.metadata.updatedByUserId").value(USER_ID))
      .andExpect(jsonPath("$.metadata.updatedDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 422 on put tag with duplicate label")
  void return422OnPutWithDuplicateLabel() throws Exception {
    var label1 = "Tag1";
    var label2 = "Tag2";
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    databaseHelper.saveTag(Tag.builder().id(id1).label(label1).build(), TENANT);
    databaseHelper.saveTag(Tag.builder().id(id2).label(label2).build(), TENANT);

    var duplicateTag = new TagDto().label(label2);
    mockMvc.perform(putTagById(id1, duplicateTag))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(DataIntegrityViolationException.class))
      .andExpect(errorTypeMatch(ErrorsHelper.ErrorType.INTERNAL.getTypeCode()));
  }

  @Test
  @DisplayName("Return 404 on update tag by ID when it is not exist")
  void return404OnPutTagByIdWhenItNotExist() throws Exception {
    var tag = new TagDto().id(UUID.randomUUID().toString()).label("important");
    mockMvc.perform(putTagById(UUID.randomUUID(), tag))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(EntityNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  // Tests for DELETE /tags/{id}
  @Test
  @DisplayName("Delete existing tag")
  void deleteExistingTag() throws Exception {
    var id = UUID.randomUUID();
    var label = "important";
    var description = "This is the first test tag";
    var tag = Tag.builder().id(id).label(label).description(description).build();
    databaseHelper.saveTag(tag, TENANT);

    mockMvc.perform(deleteById(id))
      .andExpect(status().isNoContent());

    var rowsInTable = databaseHelper.countRowsInTable(TENANT);
    assertEquals(0, rowsInTable);
  }

  @Test
  @DisplayName("Return 404 on delete tag by ID when it is not exist")
  void return404OnDeleteTagByIdWhenItNotExist() throws Exception {
    mockMvc.perform(deleteById(UUID.randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(EntityNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  private MockHttpServletRequestBuilder postTag(TagDto duplicateTag) {
    return post("/tags")
      .headers(defaultHeaders())
      .content(asJsonString(duplicateTag));
  }

  private MockHttpServletRequestBuilder getTagById(Object id) {
    return get("/tags/{id}", id)
      .headers(defaultHeaders());
  }

  private MockHttpServletRequestBuilder putTagById(UUID id, TagDto updatedTag) {
    return put("/tags/{id}", id)
      .content(asJsonString(updatedTag))
      .headers(defaultHeaders());
  }

  private MockHttpServletRequestBuilder deleteById(UUID id) {
    return delete("/tags/{id}", id)
      .headers(defaultHeaders());
  }

  private ResultMatcher errorMessageMatch(Matcher<String> errorMessageMatcher) {
    return jsonPath("$.errors.[0].message", errorMessageMatcher);
  }

  private ResultMatcher errorTypeMatch(String value) {
    return jsonPath("$.errors.[0].type").value(value);
  }

  private ResultMatcher labelMatch(String prefix, Matcher<String> labelMatcher) {
    return jsonPath(prefix + ".label", labelMatcher);
  }

  private ResultMatcher descriptionMatch(String prefix, Matcher<String> descriptionMatcher) {
    return jsonPath(prefix + ".description", descriptionMatcher);
  }

  private ResultMatcher idMatch(String prefix, Matcher<String> idMatcher) {
    return jsonPath(prefix + ".id", idMatcher);
  }

  private <T> ResultMatcher exceptionMatch(Class<T> type) {
    return result -> assertThat(result.getResolvedException(), instanceOf(type));
  }

}
