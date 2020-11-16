package org.openstreetmap.josm.plugins.pt_assistant.actions.routinghelper;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.pt_assistant.utils.PTIcons;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;

public class BusTransportMode implements ITransportMode {

    private static final List<String> suitableHighwaysForBus = Stream.concat(
        Stream.of("unclassified", "residential", "service", "living_street", "cyclestreet"),
        Stream.of("motorway", "trunk", "primary", "secondary", "tertiary").flatMap(it -> Stream.of(it, it + "_link"))
    ).collect(Collectors.toList());

    @Override
    public boolean canTraverseWay(@NotNull final IWay<?> way, @NotNull final WayTraversalDirection direction) {
        final String onewayValue = way.get("oneway");
        return (
            way.hasTag("highway", suitableHighwaysForBus)
            || way.hasTag("psv", "yes") || way.hasTag ("bus", "yes")
        )
        && (
            onewayValue == null
            || "no".equals(way.get("oneway:bus"))
            || ("yes".equals(onewayValue) && direction == WayTraversalDirection.FORWARD)
            || ("-1".equals(onewayValue) && direction == WayTraversalDirection.BACKWARD)
        );
    }

    @Override
    public boolean canBeUsedForRelation(@NotNull final IRelation<?> relation) {
        return relation.hasTag("type", "route") && relation.hasTag("route", "bus", "coach", "minibus");
    }

    @Override
    public boolean canTurn(@NotNull final Way from, @NotNull final Node via, @NotNull final Way to) {
        final Set<Relation> restrictionRelations = from.getReferrers().stream()
            .map(it -> it.getType() == OsmPrimitiveType.RELATION ? (Relation) it : null)
            .filter(Objects::nonNull)
            .filter(it -> "restriction".equals(it.get("type")) || "restriction:bus".equals(it.get("type")))
            .filter(it -> it.findRelationMembers("from").contains(from))
            .filter(it -> it.findRelationMembers("via").contains(via))
            .filter(it -> it.findRelationMembers("to").contains(to))
            .collect(Collectors.toSet());
        for (Relation restrictionRelation : restrictionRelations) {
            final String restriction = restrictionRelation.get("restriction");
            final String except = restrictionRelation.get("except");
            if (restriction.startsWith("no_") && !except.contains("psv")) {
                return false;
            }
        }

        return from.containsNode(via) && to.containsNode(via);
    }

    @Override
    public ImageProvider getIcon() {
        return PTIcons.BUS;
    }

    @Override
    public String getName() {
        return I18n.marktr("bus");
    }
}
