package org.folio.tags.dao.repository;

import java.util.UUID;

import org.folio.spring.cql.JpaCqlRepository;
import org.folio.tags.dao.model.Tag;

public interface TagRepository extends JpaCqlRepository<Tag, UUID> {

}
