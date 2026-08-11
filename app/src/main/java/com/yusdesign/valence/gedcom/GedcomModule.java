package com.yusdesign.valence.gedcom;

import android.content.Context;
import android.net.Uri;
import android.view.Menu;
import com.yusdesign.valence.module.UmlModule;
import com.yusdesign.valence.model.UmlProject;
import java.util.Set;

/**
 * GEDCOM module for Valence. Supports both 5.5.1 and 7.0.0 formats.
 */
public class GedcomModule implements UmlModule {
    public static final String MODULE_ID = "gedcom.parser";
    public static final Version VERSION = new Version(1, 0, 0);
    
    private UmlCoreEngine engine;
    private GedcomDataProvider dataProvider;
    private boolean enabled = false;
    
    @Override
    public String getModuleId() { return MODULE_ID; }
    
    @Override
    public String getDisplayName() { return "GEDCOM Parser v1.0.0"; }
    
    @Override
    public Version getVersion() { return VERSION; }
    
    @Override
    public Set<Feature> getProvidedFeatures() {
        return Set.of(Feature.PARSER_GEDCOM_551, Feature.PARSER_GEDCOM_700);
    }
    
    @Override
    public void init(UmlCoreEngine engine) {
        this.engine = engine;
        this.dataProvider = new GedcomDataProvider();
        
        // Register as file handler for .ged files
        engine.registerFileHandler(".ged", dataProvider);
        engine.registerImporter(dataProvider);
        engine.addToolbarExtension(this::addGedcomMenuItems);
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public void shutdown() {
        // Cleanup resources if needed
    }
    
    private void addGedcomMenuItems(Menu menu) {
        MenuItem item = menu.add("Import GEDCOM File");
        item.setIcon(android.R.drawable.ic_menu_upload);
        // The click handling will be done in MainActivity
    }
}
