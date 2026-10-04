package craken.visualization.model.relation;

import craken.visualization.api.ViewLocation;

public record CompositionLink(ViewLocation parent, ViewLocation child, int slot, int semanticIncrement) {}
