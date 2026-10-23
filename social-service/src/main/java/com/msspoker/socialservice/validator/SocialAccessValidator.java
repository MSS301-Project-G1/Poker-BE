package com.msspoker.socialservice.validator;

import com.msspoker.socialservice.exception.ServiceException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SocialAccessValidator {
  public void distinctAccounts(UUID a, UUID b) {
    if (a.equals(b)) throw ServiceException.invalid("Không thể thao tác với chính mình.");
  }

  public void requestDirection(String direction) {
    if (!Set.of("INCOMING", "OUTGOING").contains(direction))
      throw ServiceException.invalid("direction phải là INCOMING hoặc OUTGOING.");
  }
}
