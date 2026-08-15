package dev.haohansmp.soulanchor.domain;

import java.util.List;
import java.util.UUID;

public record SharedAnchorGroup(UUID ownerId, List<Anchor> anchors) {
}
