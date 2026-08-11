package com.yusdesign.valence.module;

import android.content.Context;
import android.net.Uri;
import android.view.Menu;

import com.yusdesign.valence.model.UmlProject;

import java.util.Set;

public interface UmlModule {
    String getModuleId();
    String getDisplayName();
    Version getVersion();
    Set<Feature> getProvidedFeatures();
    void init(UmlCoreEngine engine);
    void setEnabled(boolean enabled);
    void shutdown();
    
    interface UmlCoreEngine {
        void registerFileHandler(String extension, UmlDataProvider provider);
        void registerImporter(UmlDataProvider provider);
        void addToolbarExtension(ToolbarExtension extension);
        void onModuleActivated(UmlModule module);
        void onModuleDeactivated(UmlModule module);
    }
    
    interface UmlDataProvider {
        String getProviderName();
        String[] getSupportedFileExtensions();
        UmlProject loadData(Context context, Uri fileUri);
        void saveData(Context context, Uri fileUri, UmlProject project);
        Version getVersion();
    }
    
    interface ToolbarExtension {
        void addMenuItems(Menu menu);
    }
    
    enum Feature {
        PARSER_GEDCOM_551,
        PARSER_GEDCOM_700,
        CODE_GENERATOR,
        STATISTICS,
        EXPORTER_JSON,
        IMPORTER_FHX,
        CUSTOM_VISUALIZATION
    }
}
