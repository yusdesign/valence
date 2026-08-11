package com.yusdesign.valence.module;

import android.content.Context;
import android.util.Log;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all Valence modules. Handles registration, activation, and file handling.
 */
public class ModuleManager implements UmlModule.UmlCoreEngine {
    private static final String TAG = "ModuleManager";
    private final Context context;
    private final Map<String, UmlModule> installedModules = new ConcurrentHashMap<>();
    private final Map<String, UmlModule> activeModules = new ConcurrentHashMap<>();
    private final Map<String, UmlModule.UmlDataProvider> fileHandlers = new ConcurrentHashMap<>();
    private final List<UmlModule.ToolbarExtension> toolbarExtensions = new ArrayList<>();
    private ModuleEventListener eventListener;
    
    public interface ModuleEventListener {
        void onModuleActivated(UmlModule module);
        void onModuleDeactivated(UmlModule module);
        void onModuleUpgraded(UmlModule oldModule, UmlModule newModule);
    }
    
    public ModuleManager(Context context) {
        this.context = context;
    }
    
    public void registerModule(UmlModule module) {
        installedModules.put(module.getModuleId(), module);
        Log.i(TAG, "Registered module: " + module.getModuleId() + " v" + module.getVersion());
    }
    
    public void enableModule(String moduleId) {
        UmlModule module = installedModules.get(moduleId);
        if (module != null && !activeModules.containsKey(moduleId)) {
            module.init(this);
            module.setEnabled(true);
            activeModules.put(moduleId, module);
            if (eventListener != null) eventListener.onModuleActivated(module);
            Log.i(TAG, "Enabled module: " + moduleId);
        }
    }
    
    public void disableModule(String moduleId) {
        UmlModule module = activeModules.remove(moduleId);
        if (module != null) {
            module.setEnabled(false);
            module.shutdown();
            if (eventListener != null) eventListener.onModuleDeactivated(module);
            Log.i(TAG, "Disabled module: " + moduleId);
        }
    }
    
    public UmlModule.UmlDataProvider getFileHandler(String extension) {
        return fileHandlers.get(extension.toLowerCase());
    }
    
    public List<UmlModule> getActiveModules() {
        return new ArrayList<>(activeModules.values());
    }
    
    // UmlCoreEngine implementation
    @Override
    public void registerFileHandler(String extension, UmlModule.UmlDataProvider provider) {
        fileHandlers.put(extension.toLowerCase(), provider);
        Log.i(TAG, "Registered file handler for: " + extension);
    }
    
    @Override
    public void registerImporter(UmlModule.UmlDataProvider provider) {
        for (String ext : provider.getSupportedFileExtensions()) {
            registerFileHandler(ext, provider);
        }
    }
    
    @Override
    public void addToolbarExtension(UmlModule.ToolbarExtension extension) {
        toolbarExtensions.add(extension);
    }
    
    @Override
    public void onModuleActivated(UmlModule module) {
        if (eventListener != null) eventListener.onModuleActivated(module);
    }
    
    @Override
    public void onModuleDeactivated(UmlModule module) {
        if (eventListener != null) eventListener.onModuleDeactivated(module);
    }
    
    public void setEventListener(ModuleEventListener listener) {
        this.eventListener = listener;
    }
    
    public List<UmlModule.ToolbarExtension> getToolbarExtensions() {
        return toolbarExtensions;
    }
}
