package org.openstreetmap.josm.plugins.pt_assistant.gui.stoparea;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.gui.dialogs.relation.actions.IRelationEditorActionAccess;
import org.openstreetmap.josm.plugins.customizepublictransportstop.OSMTags;
import org.openstreetmap.josm.plugins.pt_assistant.gui.utils.AbstractTabManager;
import org.openstreetmap.josm.plugins.pt_assistant.gui.linear.RelationAccess;
import org.openstreetmap.josm.plugins.pt_assistant.gui.utils.ZoomSaver;
import org.openstreetmap.josm.plugins.pt_assistant.utils.StopUtils;

public class StopVicinityTabManager extends AbstractTabManager<StopVicinityPanel> {
    // Used so that zoom gets not lost when re-creating the view due to changes in the relation.
    private final ZoomSaver zoomSaver = new ZoomSaver();

    public StopVicinityTabManager(IRelationEditorActionAccess editorAccess) {
        super(editorAccess);
    }

    @Override
    protected TabAndDisplay<StopVicinityPanel> getTabToShow(IRelationEditorActionAccess editorAccess) {
        return new TabAndDisplay<StopVicinityPanel>() {
            @Override
            public boolean shouldDisplay() {
                RelationAccess tagged = RelationAccess.of(editorAccess);
                return StopUtils.isStopArea(tagged);
            }

            @Override
            public StopVicinityPanel getTabContent() {
                return new StopVicinityPanel(editorAccess, zoomSaver);
            }

            @Override
            public String getTitle() {
                return tr("Vicinity");
            }
        };
    }

    @Override
    protected void dispose(StopVicinityPanel view) {
        view.dispose();
    }
}
