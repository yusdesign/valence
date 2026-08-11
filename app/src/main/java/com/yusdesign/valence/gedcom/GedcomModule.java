package com.yusdesign.valence.gedcom;

import android.view.Menu;
import android.view.MenuItem;

import com.yusdesign.valence.module.UmlModule;
import com.yusdesign.valence.module.Version;

import java.util.Set;

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
        // MenuItem is now properly imported
        item.setIcon(android.R.drawable.ic_menu_upload);
    }
}
