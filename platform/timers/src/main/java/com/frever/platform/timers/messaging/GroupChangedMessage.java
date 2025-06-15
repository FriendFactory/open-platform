package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GroupChangedMessage(@JsonProperty("GroupId") long groupId,
                                  @JsonProperty("MainCharacterId") Long mainCharacterId,
                                  @JsonProperty("TaxationCountryId") Long taxationCountryId,
                                  @JsonProperty("DefaultLanguageId") long defaultLanguageId,
                                  @JsonProperty("CharacterFiles") String characterFiles) {

}
