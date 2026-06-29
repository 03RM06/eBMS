package gov.brgy.ebms.usermgmt.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignRolesRequest(@NotNull List<Long> roleIds) {}
