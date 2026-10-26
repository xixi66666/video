package cn.creator.workbench.api;

import com.google.gson.JsonObject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record WorkspaceCommand(@NotBlank String type, @NotNull JsonObject payload, @NotNull @PositiveOrZero Long expectedVersion) {}
