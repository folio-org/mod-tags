package org.folio.tags.support;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import org.folio.spring.FolioModuleMetadata;
import org.folio.tags.dao.model.Tag;

public class DatabaseHelper {

  private final FolioModuleMetadata metadata;
  private final JdbcTemplate jdbcTemplate;

  public DatabaseHelper(FolioModuleMetadata metadata, JdbcTemplate jdbcTemplate) {
    this.metadata = metadata;
    this.jdbcTemplate = jdbcTemplate;
  }

  public int countRowsInTable(String tenant) {
    return JdbcTestUtils.countRowsInTable(jdbcTemplate, getTableName(tenant));
  }

  public String getTableName(String tenantId) {
    return metadata.getDBSchemaName(tenantId) + "." + "tags";
  }

  public void clearTable(String tenant) {
    JdbcTestUtils.deleteFromTables(jdbcTemplate, getTableName(tenant));
  }

  public void saveTag(Tag tag, String tenant) {
    var sql = "INSERT INTO " + getTableName(tenant) + " (id, label, description) VALUES (?, ?, ?)";
    jdbcTemplate.update(sql, tag.getId(), tag.getLabel(), tag.getDescription());
  }

  public void saveTags(List<Tag> tagList, String tenant) {
    var sql = "INSERT INTO " + getTableName(tenant) + " (label, description) VALUES (?, ?)";
    var args = tagList.stream()
      .map(tag -> new Object[] {tag.getLabel(), tag.getDescription()})
      .collect(Collectors.toList());
    jdbcTemplate.batchUpdate(sql, args);
  }
}
