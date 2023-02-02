package org.folio.tags.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tags")
@EntityListeners(AuditingEntityListener.class)
public class Tag {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(unique = true, nullable = false)
  private String label;

  private String description;

  @CreatedDate
  @Column(name = "created_date", nullable = false, updatable = false)
  private Timestamp createdDate;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @LastModifiedDate
  @Column(name = "updated_date")
  private Timestamp updatedDate;

  @LastModifiedBy
  @Column(name = "updated_by")
  private UUID updatedBy;

}
