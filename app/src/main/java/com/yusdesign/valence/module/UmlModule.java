package com.yusdesign.valence.module;

import android.content.Context;
import android.net.Uri;
import com.yusdesign.valence.model.UmlProject;
import java.util.Set;

/**
 * Core interface for all Valence modules.
 * Implement this to add new functionality (GEDCOM parser, code generator, etc.)
 */
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
        void addMenuItems(android.view.Menu menu);
    }
    
    class Version implements Comparable<Version> {
        private final int major, minor, patch;
        
        public Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }
        
        public int getMajor() { return major; }
        public int getMinor() { return minor; }
        public int getPatch() { return patch; }
        
        @Override
        public int compareTo(Version other) {
            if (this.major != other.major) return this.major - other.major;
            if (this.minor != other.minor) return this.minor - other.minor;
            return this.patch - other.patch;
        }
        
        @Override
        public String toString() { return major + "." + minor + "." + patch; }
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
