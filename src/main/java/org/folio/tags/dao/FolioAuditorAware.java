package org.folio.tags.dao;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.spring.FolioExecutionContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolioAuditorAware implements AuditorAware<UUID> {

  private final FolioExecutionContext folioExecutionContext;

  @Override
  public Optional<UUID> getCurrentAuditor() {
    return Optional.ofNullable(folioExecutionContext.getUserId());
  }
}
