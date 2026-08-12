package com.yusdesign.valence.controller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.yusdesign.valence.R;
import com.yusdesign.valence.model.TypeNameComparator;
import com.yusdesign.valence.model.UmlClass;
import com.yusdesign.valence.model.UmlProject;
import com.yusdesign.valence.model.UmlRelation;
import com.yusdesign.valence.model.UmlType;
import com.yusdesign.valence.view.AttributeEditorFragment;
import com.yusdesign.valence.view.ClassEditorFragment;
import com.yusdesign.valence.view.GraphFragment;
import com.yusdesign.valence.view.GraphView;
import com.yusdesign.valence.view.MethodEditorFragment;
import com.yusdesign.valence.view.ParameterEditorFragment;

// Module system imports
import com.yusdesign.valence.module.ModuleManager;
import com.yusdesign.valence.module.UmlModule;
import com.yusdesign.valence.gedcom.GedcomModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements FragmentObserver,
        GraphView.GraphViewObserver {

    // ====== STATIC INITIALIZER ======
    static {
        Log.e("ValenceDebug", "=========================================");
        Log.e("ValenceDebug", "MainActivity STATIC INITIALIZER");
        Log.e("ValenceDebug", "=========================================");
    }

    // ====== Core App Fields ======
    private UmlProject mProject;
    private boolean mExpectingTouchLocation = false;
    private Purpose mPurpose = FragmentObserver.Purpose.NONE;
    private Toolbar mToolbar;
    private DrawerLayout mDrawerLayout;

    // ====== Permission Fields ======
    private static boolean sWriteExternalStoragePermission = true;
    private static boolean sReadExternalStoragePermission = true;
    private static final int WRITE_EXTERNAL_STORAGE_INDEX = 0;
    private static final int READ_EXTERNAL_STORAGE_INDEX = 1;

    // ====== Back Press Fields ======
    private long mFirstBackPressedTime = 0;
    private static long DOUBLE_BACK_PRESSED_DELAY = 2000;
    private OnBackPressedCallback mOnBackPressedCallback;

    // ====== Click Debouncing ======
    private long mLastClickTime = 0;

    private boolean isClickTooFast() {
        long now = System.currentTimeMillis();
        if (now - mLastClickTime < 500) {
            Log.e("ValenceUI", "Click too fast - ignoring");
            return true;
        }
        mLastClickTime = now;
        return false;
    }

    // ====== Fragment Declarations ======
    private GraphFragment mGraphFragment;
    private ClassEditorFragment mClassEditorFragment;
    private AttributeEditorFragment mAttributeEditorFragment;
    private MethodEditorFragment mMethodEditorFragment;
    private ParameterEditorFragment mParameterEditorFragment;

    private static final String GRAPH_FRAGMENT_TAG = "graphFragment";
    private static final String CLASS_EDITOR_FRAGMENT_TAG = "classEditorFragment";
    private static final String ATTRIBUTE_EDITOR_FRAGMENT_TAG = "attributeEditorFragment";
    private static final String METHOD_EDITOR_FRAGMENT_TAG = "methodEditorFragment";
    private static final String PARAMETER_EDITOR_FRAGMENT_TAG = "parameterEditorFragment";

    // ====== Intent Constants ======
    private static final String SHARED_PREFERENCES_PROJECT_NAME = "sharedPreferencesProjectName";
    private static final int INTENT_CREATE_DOCUMENT_EXPORT_PROJECT = 1000;
    private static final int INTENT_OPEN_DOCUMENT_IMPORT_PROJECT = 2000;
    private static final int INTENT_CREATE_DOCUMENT_EXPORT_CUSTOM_TYPES = 3000;
    private static final int INTENT_OPEN_DOCUMENT_IMPORT_CUSTOM_TYPES = 4000;
    private static final int INTENT_IMPORT_GEDCOM = 5000;
    private static final int REQUEST_PERMISSION = 6000;

    // ====== Module System Fields ======
    private ModuleManager moduleManager;
    private static final String GEDCOM_MODULE_ID = "gedcom.parser";

    // ====== Views Declaration ======
    private FrameLayout mMainActivityFrame;
    private GraphView mGraphView;

    // ====== CONSTRUCTOR ======
    public MainActivity() {
        Log.e("ValenceDebug", "MainActivity CONSTRUCTOR called");
    }

    // ====== Lifecycle Methods ======

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e("ValenceDebug", "onCreate() - START");
    
        try {
            // Set up crash handler, as early as possible
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                Log.e("ValenceCrash", "!!! UNCAUGHT EXCEPTION !!!", throwable);
                // Show a Toast so you can see the error
                runOnUiThread(() -> {
                    try {
                        Toast.makeText(MainActivity.this, 
                            "Error: " + throwable.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    } catch (Exception ignored) {}
                });
            });
    
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            // Force the hamburger icon to show
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
    
            // ====== Set up the drawer ListView ======
            ListView drawerList = findViewById(R.id.drawer_list);
            String[] menuItems = {
                "New Project", 
                "Load Project", 
                "Import GEDCOM",  // Position 2
                "Save As...", 
                "Merge Project", 
                "Delete Project"
            };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, menuItems);
            drawerList.setAdapter(adapter);
    
            // In onCreate() - drawerList setup
            drawerList.setOnItemClickListener((parent, view, position, id) -> {
                // Debounce
                if (isClickTooFast()) {
                    return;
                }
                
                switch (position) {
                    case 0: drawerMenuNewProject(); break;
                    case 1: drawerMenuLoadProject(); break;
                    case 2: importGedcomFile(); break;  // GEDCOM vv…
                    case 3: drawerMenuSaveAs(); break;
                    case 4: drawerMenuMerge(); break;
                    case 5: drawerMenuDeleteProject(); break;
                }
                mDrawerLayout.closeDrawer(GravityCompat.START);
            });
    
            // ====== Find views ======
            mMainActivityFrame = findViewById(R.id.activity_main_frame);
            mDrawerLayout = findViewById(R.id.activity_main_drawer);
    
            // ====== Initialize UmlType ======
            UmlType.clearUmlTypes();
            UmlType.initializePrimitiveUmlTypes(this);
            UmlType.initializeCustomUmlTypes(this);
    
            // ====== Get preferences ======
            getPreferences();
    
            // ====== Configure UI components ======
            configureToolbar();
            configureDrawerLayout();
            // configureNavigationView();  // REMOVED - replaced by ListView
    
            // ====== Configure graph fragment ======
            configureAndDisplayGraphFragment(R.id.activity_main_frame);
    
            // ====== Initialize module system ======
            initModuleSystem();
    
            // ====== Set up back callback ======
            createOnBackPressedCallback();
            setOnBackPressedCallback();
    
            // ====== Request permissions ======
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkPermissions();
            }
    
            Log.e("ValenceDebug", "onCreate() - COMPLETED SUCCESSFULLY!");
    
        } catch (Exception e) {
            Log.e("ValenceDebug", "onCreate() - FAILED!", e);
            Toast.makeText(this, "CRASH: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.e("ValenceDebug", "onCreateOptionsMenu() - START");
        try {
            getMenuInflater().inflate(R.menu.activity_main_toolbar_menu, menu);
            Log.e("ValenceDebug", "Base menu inflated");
    
            // Check if GEDCOM module is enabled
            if (moduleManager != null) {
                // Find the GEDCOM menu item from XML
                MenuItem gedcomItem = menu.findItem(R.id.toolbar_menu_import_gedcom);
                if (gedcomItem != null) {
                    gedcomItem.setVisible(true);
                    Log.e("ValenceDebug", "GEDCOM menu item found and set visible");
                } else {
                    Log.e("ValenceDebug", "GEDCOM menu item not found in XML - adding dynamically");
                    // Fallback: add dynamically if not in XML
                    gedcomItem = menu.add(Menu.NONE, R.id.toolbar_menu_import_gedcom, Menu.NONE, "Import GEDCOM");
                    gedcomItem.setIcon(android.R.drawable.ic_menu_upload);
                    gedcomItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
            } else {
                Log.e("ValenceDebug", "moduleManager is null, GEDCOM not available");
            }
    
            MenuCompat.setGroupDividerEnabled(menu, true);
            Log.e("ValenceDebug", "onCreateOptionsMenu() - COMPLETED");
            return super.onCreateOptionsMenu(menu);
        } catch (Exception e) {
            Log.e("ValenceDebug", "onCreateOptionsMenu() - FAILED", e);
            return super.onCreateOptionsMenu(menu);
        }
    }

    @Override
    protected void onStart() {
        Log.e("ValenceDebug", "onStart() - START");
        try {
            super.onStart();

            mGraphView = findViewById(R.id.graphview);
            Log.e("ValenceDebug", "mGraphView = " + (mGraphView != null));
            if (mGraphView != null) {
                mGraphView.setUmlProject(mProject);
                Log.e("ValenceDebug", "mGraphView.setUmlProject() called");
            }
            Log.i("TEST", "onStart");
            Log.e("ValenceDebug", "onStart() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "onStart() - FAILED", e);
        }
    }

    @Override
    protected void onDestroy() {
        Log.e("ValenceDebug", "onDestroy() - START");
        try {
            super.onDestroy();

            mProject.save(getApplicationContext());
            Log.i("TEST", "save : project");
            savePreferences();
            Log.i("TEST", "save : preferences");
            UmlType.saveCustomUmlTypes(this);
            Log.i("TEST", "save : custom types");
            Log.e("ValenceDebug", "onDestroy() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "onDestroy() - FAILED", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onResume() {
        Log.e("ValenceDebug", "onResume() - START");
        try {
            super.onResume();
            checkPermissions();
            Log.e("ValenceDebug", "onResume() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "onResume() - FAILED", e);
        }
    }

    // ====== Module System Initialization ======

    /**
    * Initializes the module system and registers the GEDCOM module.
    * This is the entry point for all Valence extensions.
    */
    private void initModuleSystem() {
        Log.e("ValenceDebug", "=========================================");
        Log.e("ValenceDebug", "initModuleSystem() - START");
        Log.e("ValenceDebug", "=========================================");

        try {
            // STEP 1: Create ModuleManager
            Log.e("ValenceDebug", "STEP 1: Creating ModuleManager...");
            moduleManager = new ModuleManager(this);
            Log.e("ValenceDebug", "STEP 1: ModuleManager created SUCCESSFULLY");

            // STEP 2: Set Event Listener
            Log.e("ValenceDebug", "STEP 2: Setting EventListener...");
            moduleManager.setEventListener(new ModuleManager.ModuleEventListener() {
                @Override
                public void onModuleActivated(UmlModule module) {
                    Log.e("ValenceDebug", "EVENT: Module ACTIVATED: " + module.getDisplayName());
                    Toast.makeText(MainActivity.this,
                            "Module activated: " + module.getDisplayName(),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onModuleDeactivated(UmlModule module) {
                    Log.e("ValenceDebug", "EVENT: Module DEACTIVATED: " + module.getDisplayName());
                }

                @Override
                public void onModuleUpgraded(UmlModule oldModule, UmlModule newModule) {
                    Log.e("ValenceDebug", "EVENT: Module UPGRADED: " + oldModule.getModuleId());
                }
            });
            Log.e("ValenceDebug", "STEP 2: EventListener set SUCCESSFULLY");

            // STEP 3: REGISTRATION IS DISABLED FOR TESTING
            // Log.e("ValenceDebug", "STEP 3: GEDCOM module registration is DISABLED for debugging");
            // Log.e("ValenceDebug", "STEP 3: If app starts, the issue is in the GEDCOM module");
            
            // Uncomment this when ready to test GEDCOM:
            // STEP 3: Create GEDCOM Module Instance
            Log.e("ValenceDebug", "STEP 3: Creating GedcomModule instance...");
            GedcomModule gedcomModule = new GedcomModule();
            Log.e("ValenceDebug", "STEP 3: GedcomModule instance created");

            // STEP 4: Register GEDCOM Module
            Log.e("ValenceDebug", "STEP 4: Registering GedcomModule...");
            moduleManager.registerModule(gedcomModule);
            Log.e("ValenceDebug", "STEP 4: GedcomModule registered");

            // STEP 5: Enable GEDCOM Module
            Log.e("ValenceDebug", "STEP 5: Enabling GedcomModule...");
            moduleManager.enableModule(GEDCOM_MODULE_ID);
            Log.e("ValenceDebug", "STEP 5: GedcomModule enabled");
            
            Log.e("ValenceDebug", "=========================================");
            Log.e("ValenceDebug", "initModuleSystem() - COMPLETED SUCCESSFULLY!");
            Log.e("ValenceDebug", "=========================================");

        } catch (Exception e) {
            Log.e("ValenceDebug", "=========================================");
            Log.e("ValenceDebug", "initModuleSystem() - FAILED!");
            Log.e("ValenceDebug", "=========================================");
            Log.e("ValenceDebug", "Exception: " + e.getMessage(), e);
            Toast.makeText(this, "Module init failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ====== Configuration Methods ======

    private void configureToolbar() {
        Log.e("ValenceDebug", "configureToolbar() - START");
        try {
            mToolbar = findViewById(R.id.main_activity_toolbar);
            Log.e("ValenceDebug", "mToolbar = " + (mToolbar != null));
            setSupportActionBar(mToolbar);
            Log.e("ValenceDebug", "configureToolbar() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "configureToolbar() - FAILED", e);
            throw e;
        }
    }

    private void configureDrawerLayout() {
	    Log.e("ValenceDebug", "configureDrawerLayout() - START");
	    try {
	        mDrawerLayout = findViewById(R.id.activity_main_drawer);
	        if (mDrawerLayout == null) {
	            Log.e("ValenceDebug", "DrawerLayout is NULL!");
	            return;
	        }
	        
	        // Create the toggle with the toolbar
	        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
	                this, 
	                mDrawerLayout, 
	                mToolbar,  // Must be the same toolbar used with setSupportActionBar
	                R.string.navigation_drawer_open, 
	                R.string.navigation_drawer_close
	        );
	        
	        // Add listener and sync
	        mDrawerLayout.addDrawerListener(toggle);
	        toggle.syncState();  // THIS IS CRITICAL - it syncs the hamburger icon
	        
	        Log.e("ValenceDebug", "configureDrawerLayout() - COMPLETED");
	    } catch (Exception e) {
	        Log.e("ValenceDebug", "configureDrawerLayout() - FAILED", e);
	    }
	}

    private void savePreferences() {
        try {
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREFERENCES_PROJECT_NAME, mProject.getName());
            editor.apply();
        } catch (Exception e) {
            Log.e("ValenceDebug", "savePreferences() - FAILED", e);
        }
    }

    private void getPreferences() {
        Log.e("ValenceDebug", "getPreferences() - START");
        try {
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            String projectName = preferences.getString(SHARED_PREFERENCES_PROJECT_NAME, null);
            Log.i("TEST", "Loaded preferences");
            if (projectName != null) {
                mProject = UmlProject.load(getApplicationContext(), projectName);
                Log.e("ValenceDebug", "Project loaded: " + projectName);
            } else {
                mProject = new UmlProject("NewProject", getApplicationContext());
                Log.e("ValenceDebug", "New project created");
            }
            Log.e("ValenceDebug", "getPreferences() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "getPreferences() - FAILED", e);
            // Create a default project if loading fails
            mProject = new UmlProject("NewProject", getApplicationContext());
        }
    }

    // ====== Back Press Handling ======

    private void createOnBackPressedCallback() {
        Log.e("ValenceDebug", "createOnBackPressedCallback() - START");
        try {
            mOnBackPressedCallback = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    onBackButtonPressed();
                }
            };
            Log.e("ValenceDebug", "createOnBackPressedCallback() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "createOnBackPressedCallback() - FAILED", e);
        }
    }

    private void setOnBackPressedCallback() {
        Log.e("ValenceDebug", "setOnBackPressedCallback() - START");
        try {
            this.getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback);
            Log.e("ValenceDebug", "setOnBackPressedCallback() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "setOnBackPressedCallback() - FAILED", e);
        }
    }

    private void onBackButtonPressed() {
        if (Calendar.getInstance().getTimeInMillis() - mFirstBackPressedTime > DOUBLE_BACK_PRESSED_DELAY) {
            mFirstBackPressedTime = Calendar.getInstance().getTimeInMillis();
            Toast.makeText(this, "Press back again to leave", Toast.LENGTH_SHORT).show();
        } else {
            finish();
        }
    }

    // ====== Fragment Management ======
    private void configureAndDisplayGraphFragment(int viewContainerId) {
        Log.e("ValenceDebug", "configureAndDisplayGraphFragment() - START");
        try {
            mGraphFragment = GraphFragment.newInstance();
            Log.e("ValenceDebug", "GraphFragment created");
            getSupportFragmentManager().beginTransaction()
                    .replace(viewContainerId, mGraphFragment, GRAPH_FRAGMENT_TAG)
                    .commitNow();
            Log.e("ValenceDebug", "GraphFragment transaction committed");
            Log.e("ValenceDebug", "configureAndDisplayGraphFragment() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "configureAndDisplayGraphFragment() - FAILED", e);
            throw e;
        }
    }

    private void configureAndDisplayClassEditorFragment(int viewContainerId, float xLocation,
                                                        float yLocation, int classOrder) {
        Log.e("ValenceDebug", "configureAndDisplayClassEditorFragment() - START");
        try {
            if (mClassEditorFragment == null) {
                mClassEditorFragment = ClassEditorFragment.newInstance(xLocation, yLocation, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mGraphFragment)
                        .add(viewContainerId, mClassEditorFragment, CLASS_EDITOR_FRAGMENT_TAG)
                        .commitNow();
                Log.e("ValenceDebug", "New ClassEditorFragment created");
            } else {
                mClassEditorFragment.updateClassEditorFragment(xLocation, yLocation, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mGraphFragment)
                        .show(mClassEditorFragment)
                        .commitNow();
                Log.e("ValenceDebug", "ClassEditorFragment updated");
            }
            Log.e("ValenceDebug", "configureAndDisplayClassEditorFragment() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "configureAndDisplayClassEditorFragment() - FAILED", e);
        }
    }

    private void configureAndDisplayAttributeEditorFragment(int viewContainerId, int attributeOrder,
                                                            int classOrder) {
        try {
            if (mAttributeEditorFragment == null) {
                mAttributeEditorFragment = AttributeEditorFragment.newInstance(
                        mClassEditorFragment.getTag(), attributeOrder, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mClassEditorFragment)
                        .add(viewContainerId, mAttributeEditorFragment, ATTRIBUTE_EDITOR_FRAGMENT_TAG)
                        .commitNow();
            } else {
                mAttributeEditorFragment.updateAttributeEditorFragment(attributeOrder, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mClassEditorFragment)
                        .show(mAttributeEditorFragment)
                        .commitNow();
            }
        } catch (Exception e) {
            Log.e("ValenceDebug", "configureAndDisplayAttributeEditorFragment() - FAILED", e);
        }
    }

    private void configureAndDisplayMethodEditorFragment(int viewContainerId, int methodOrder,
                                                         int classOrder) {
        try {
            if (mMethodEditorFragment == null) {
                mMethodEditorFragment = MethodEditorFragment.newInstance(
                        mClassEditorFragment.getTag(), methodOrder, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mClassEditorFragment)
                        .add(viewContainerId, mMethodEditorFragment, METHOD_EDITOR_FRAGMENT_TAG)
                        .commitNow();
            } else {
                mMethodEditorFragment.updateMethodEditorFragment(methodOrder, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mClassEditorFragment)
                        .show(mMethodEditorFragment)
                        .commitNow();
            }
        } catch (Exception e) {
            Log.e("ValenceDebug", "configureAndDisplayMethodEditorFragment() - FAILED", e);
        }
    }

    private void configureAndDisplayParameterEditorFragment(int viewContainerId, int parameterOrder,
                                                            int methodOrder, int classOrder) {
        try {
            if (mParameterEditorFragment == null) {
                mParameterEditorFragment = ParameterEditorFragment.newInstance(
                        mMethodEditorFragment.getTag(), parameterOrder, methodOrder, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mMethodEditorFragment)
                        .add(viewContainerId, mParameterEditorFragment, PARAMETER_EDITOR_FRAGMENT_TAG)
                        .commitNow();
            } else {
                mParameterEditorFragment.updateParameterEditorFragment(parameterOrder, methodOrder, classOrder);
                getSupportFragmentManager().beginTransaction()
                        .hide(mMethodEditorFragment)
                        .show(mParameterEditorFragment)
                        .commitNow();
            }
        } catch (Exception e) {
            Log.e("ValenceDebug", "configureAndDisplayParameterEditorFragment() - FAILED", e);
        }
    }

    // ====== Getters and Setters ======

    public void setProject(UmlProject project) {
        mProject = project;
    }

    // ====== Callback Methods ======

    @Override
    public void setPurpose(Purpose purpose) {
        mPurpose = purpose;
    }

    @Override
    public void closeClassEditorFragment(Fragment fragment) {
        try {
            getSupportFragmentManager().beginTransaction()
                    .hide(fragment)
                    .show(mGraphFragment)
                    .commitNow();
            if (mGraphView != null) mGraphView.invalidate();
        } catch (Exception e) {
            Log.e("ValenceDebug", "closeClassEditorFragment() - FAILED", e);
        }
    }

    @Override
    public void closeAttributeEditorFragment(Fragment fragment) {
        try {
            getSupportFragmentManager().beginTransaction()
                    .hide(fragment)
                    .show(mClassEditorFragment)
                    .commit();
            if (mClassEditorFragment != null) mClassEditorFragment.updateLists();
        } catch (Exception e) {
            Log.e("ValenceDebug", "closeAttributeEditorFragment() - FAILED", e);
        }
    }

    @Override
    public void closeMethodEditorFragment(Fragment fragment) {
        try {
            getSupportFragmentManager().beginTransaction()
                    .hide(fragment)
                    .show(mClassEditorFragment)
                    .commitNow();
            if (mClassEditorFragment != null) mClassEditorFragment.updateLists();
        } catch (Exception e) {
            Log.e("ValenceDebug", "closeMethodEditorFragment() - FAILED", e);
        }
    }

    @Override
    public void closeParameterEditorFragment(Fragment fragment) {
        try {
            getSupportFragmentManager().beginTransaction()
                    .hide(fragment)
                    .show(mMethodEditorFragment)
                    .commitNow();
            if (mMethodEditorFragment != null) mMethodEditorFragment.updateLists();
        } catch (Exception e) {
            Log.e("ValenceDebug", "closeParameterEditorFragment() - FAILED", e);
        }
    }

    @Override
    public void openAttributeEditorFragment(int attributeOrder, int classOrder) {
        configureAndDisplayAttributeEditorFragment(R.id.activity_main_frame, attributeOrder, classOrder);
    }

    @Override
    public void openMethodEditorFragment(int methodOrder, int classOrder) {
        configureAndDisplayMethodEditorFragment(R.id.activity_main_frame, methodOrder, classOrder);
    }

    @Override
    public void openParameterEditorFragment(int parameterOrder, int methodOrder, int classOrder) {
        configureAndDisplayParameterEditorFragment(R.id.activity_main_frame,
                parameterOrder, methodOrder, classOrder);
    }

    @Override
    public UmlProject getProject() {
        return this.mProject;
    }

    // ====== GraphViewObserver Methods ======

    @Override
    public boolean isExpectingTouchLocation() {
        return mExpectingTouchLocation;
    }

    @Override
    public void createClass(float xLocation, float yLocation) {
        configureAndDisplayClassEditorFragment(R.id.activity_main_frame, xLocation, yLocation, -1);
    }

    @Override
    public void editClass(UmlClass umlClass) {
        configureAndDisplayClassEditorFragment(R.id.activity_main_frame, 0, 0, umlClass.getClassOrder());
    }

    @Override
    public void createRelation(UmlClass startClass, UmlClass endClass,
                               UmlRelation.UmlRelationType relationType) {
        if (!mProject.relationAlreadyExistsBetween(startClass, endClass)) {
            mProject.addUmlRelation(new UmlRelation(startClass, endClass, relationType));
        }
    }

    // ====== Navigation View Events ======
    
    // ====== Navigation View Called Methods ======

    private void drawerMenuSaveAs() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText editText = new EditText(this);
        editText.setText(mProject.getName());
        builder.setTitle("Save as")
                .setMessage("Enter new name :")
                .setView(editText)
                .setNegativeButton("CANCEL", (dialogInterface, i) -> {})
                .setPositiveButton("OK", (dialogInterface, i) -> saveAs(editText.getText().toString()))
                .create()
                .show();
    }

    private void drawerMenuNewProject() {
        runOnUiThread(() -> {
            try {
                mProject.save(this);
                UmlType.clearProjectUmlTypes();
                mProject = new UmlProject("NewProject", this);
                if (mGraphView != null) mGraphView.setUmlProject(mProject);
            } catch (Exception e) {
                Log.e("ValenceUI", "Error in new project", e);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawerMenuLoadProject() {
        mProject.save(this);

        final Spinner spinner = new Spinner(this);
        spinner.setAdapter(projectDirectoryAdapter());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Load project")
                .setMessage("Choose project to load :")
                .setView(spinner)
                .setNegativeButton("CANCEL", (dialogInterface, i) -> {})
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    String fileName = spinner.getSelectedItem().toString();
                    if (fileName != null) {
                        UmlType.clearProjectUmlTypes();
                        mProject = UmlProject.load(getApplicationContext(), fileName);
                        if (mGraphView != null) mGraphView.setUmlProject(mProject);
                        // updateNavigationView();
                    }
                })
                .create()
                .show();
    }

    private void drawerMenuDeleteProject() {
        final Context context = this;

        final Spinner spinner = new Spinner(this);
        spinner.setAdapter(projectDirectoryAdapter());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete project")
                .setMessage("Choose project to delete :")
                .setView(spinner)
                .setNegativeButton("CANCEL", (dialogInterface, i) -> {})
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    String fileName = spinner.getSelectedItem().toString();
                    if (fileName != null) {
                        File pathName = new File(getFilesDir(), UmlProject.PROJECT_DIRECTORY);
                        final File file = new File(pathName, fileName);
                        AlertDialog.Builder alert = new AlertDialog.Builder(context);
                        alert.setTitle("Delete Project")
                                .setMessage("Are you sure you want to delete " + fileName + " ?")
                                .setNegativeButton("NO", (dialog, which) -> {})
                                .setPositiveButton("YES", (dialog, which) -> file.delete())
                                .create()
                                .show();
                    }
                })
                .create()
                .show();
    }

    private void drawerMenuMerge() {
        final Spinner spinner = new Spinner(this);
        spinner.setAdapter(projectDirectoryAdapter());
        final Context currentContext = this;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Merge project")
                .setMessage("Choose project to merge")
                .setView(spinner)
                .setNegativeButton("CANCEL", (dialogInterface, i) -> {})
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    String fileName = spinner.getSelectedItem().toString();
                    if (fileName != null) {
                        UmlProject project = UmlProject.load(getApplicationContext(), fileName);
                        mProject.mergeWith(project);
                        if (mGraphView != null) mGraphView.invalidate();
                    }
                })
                .create()
                .show();
    }

    private ArrayAdapter<String> projectDirectoryAdapter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                IOUtils.sortedFiles(new File(getFilesDir(), UmlProject.PROJECT_DIRECTORY)));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    // ====== Option Menu Events ======
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        
        // Debounce - prevent rapid clicks
        if (isClickTooFast()) {
            return true;
        }
        
        // ====== Project Group ======
        if (itemId == R.id.toolbar_menu_export) {
            if (sWriteExternalStoragePermission) menuItemExport();
            return true;
        }
        if (itemId == R.id.toolbar_menu_import) {
            if (sReadExternalStoragePermission) menuItemImport();
            return true;
        }
        if (itemId == R.id.toolbar_menu_import_gedcom) {
            importGedcomFile();
            return true;
        }
        
        // ====== Custom Types Group ======
        if (itemId == R.id.toolbar_menu_create_custom_type) {
            menuCreateCustomType();
            return true;
        }
        if (itemId == R.id.toolbar_menu_delete_custom_types) {
            menuDeleteCustomTypes();
            return true;
        }
        if (itemId == R.id.toolbar_menu_export_custom_types) {
            if (sWriteExternalStoragePermission) menuExportCustomTypes();
            return true;
        }
        if (itemId == R.id.toolbar_menu_import_custom_types) {
            if (sReadExternalStoragePermission) menuImportCustomTypes();
            return true;
        }
        
        // ====== Help Group ======
        if (itemId == R.id.toolbar_menu_help) {
            menuHelp();
            return true;
        }
        
        return super.onOptionsItemSelected(menuItem);
    }
    // ====== Menu Item Called Methods ======

    private void menuItemExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/*");
        startActivityForResult(intent, INTENT_CREATE_DOCUMENT_EXPORT_PROJECT);
    }

    private void menuItemImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        startActivityForResult(intent, INTENT_OPEN_DOCUMENT_IMPORT_PROJECT);
    }

    private void menuCreateCustomType() {
        final EditText editText = new EditText(this);
        final Context context = getApplicationContext();
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle("Create custom type")
                .setMessage("Enter custom type name :")
                .setView(editText)
                .setNegativeButton("CANCEL", (dialogInterface, i) -> {})
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    String typeName = editText.getText().toString();
                    if (typeName.equals("")) {
                        Toast.makeText(context, "Failed : name cannot be blank", Toast.LENGTH_SHORT).show();
                    } else if (UmlType.containsUmlTypeNamed(typeName)) {
                        Toast.makeText(context, "Failed : this name is already used", Toast.LENGTH_SHORT).show();
                    } else {
                        UmlType.createUmlType(typeName, UmlType.TypeLevel.CUSTOM);
                        Toast.makeText(context, "Custom type created", Toast.LENGTH_SHORT).show();
                    }
                })
                .create()
                .show();
    }

    private void menuDeleteCustomTypes() {
        final ListView listView = new ListView(this);
        List<String> listArray = new ArrayList<>();
        for (UmlType t : UmlType.getUmlTypes()) {
            if (t.isCustomUmlType()) listArray.add(t.getName());
        }
        Collections.sort(listArray, new TypeNameComparator());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, listArray);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(adapter);

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle("Delete custom types")
                .setMessage("Check custom types to delete")
                .setView(listView)
                .setNegativeButton("CANCEL", (dialogInterface, i) -> {})
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    SparseBooleanArray checkMapping = listView.getCheckedItemPositions();
                    UmlType t;
                    for (int j = 0; j < checkMapping.size(); j++) {
                        if (checkMapping.valueAt(j)) {
                            t = UmlType.valueOf(listView.getItemAtPosition(checkMapping.keyAt(j)).toString(),
                                    UmlType.getUmlTypes());
                            UmlType.removeUmlType(t);
                            mProject.removeParametersOfType(t);
                            mProject.removeMethodsOfType(t);
                            mProject.removeAttributesOfType(t);
                            if (mGraphView != null) mGraphView.invalidate();
                        }
                    }
                })
                .create()
                .show();
    }

    private void menuExportCustomTypes() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/*");
        startActivityForResult(intent, INTENT_CREATE_DOCUMENT_EXPORT_CUSTOM_TYPES);
    }

    private void menuImportCustomTypes() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        startActivityForResult(intent, INTENT_OPEN_DOCUMENT_IMPORT_CUSTOM_TYPES);
    }

    private void menuHelp() {
		    runOnUiThread(() -> {
		        AlertDialog.Builder adb = new AlertDialog.Builder(this);
		        adb.setTitle("Help")
		                .setMessage(Html.fromHtml(IOUtils.readRawHtmlFile(this, R.raw.help_html)))
		                .setPositiveButton("OK", (dialog, which) -> {})
		                .create()
		                .show();
		    });
		}

    // ====== GEDCOM Import ======

    private void importGedcomFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/gedcom"});
        startActivityForResult(intent, INTENT_IMPORT_GEDCOM);
    }

    private void processGedcomFile(Uri fileUri) {
        if (moduleManager == null) {
            Toast.makeText(this, "Module system not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        UmlModule.UmlDataProvider provider = moduleManager.getFileHandler(".ged");
        if (provider == null) {
            Toast.makeText(this, "GEDCOM module not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            UmlProject project = provider.loadData(this, fileUri);
            if (project != null) {
                mProject = project;
                if (mGraphView != null) mGraphView.setUmlProject(mProject);
                // updateNavigationView();
                Toast.makeText(this,
                        "GEDCOM imported successfully!\n" +
                                "Found " + project.getUmlClasses().size() + " classes",
                        Toast.LENGTH_LONG).show();
                Log.i("GEDCOM", "Imported project with " +
                        project.getUmlClasses().size() + " classes and " +
                        project.getUmlRelations().size() + " relations");
            } else {
                Toast.makeText(this, "Failed to parse GEDCOM file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("GEDCOM", "Error processing GEDCOM file", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ====== Intents ======

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // Handle GEDCOM import
        if (requestCode == INTENT_IMPORT_GEDCOM && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                processGedcomFile(fileUri);
            }
            return;
        }

        // Existing intent handling
        if (requestCode == INTENT_CREATE_DOCUMENT_EXPORT_PROJECT && resultCode == RESULT_OK) {
            Uri fileNameUri = data.getData();
            mProject.exportProject(this, fileNameUri);
        } else if (requestCode == INTENT_OPEN_DOCUMENT_IMPORT_PROJECT && resultCode == RESULT_OK) {
            Uri fileNameUri = data.getData();
            UmlType.clearProjectUmlTypes();
            mProject = UmlProject.importProject(this, fileNameUri);
            if (mGraphView != null) mGraphView.setUmlProject(mProject);
        } else if (requestCode == INTENT_CREATE_DOCUMENT_EXPORT_CUSTOM_TYPES && resultCode == RESULT_OK) {
            Uri fileNameUri = data.getData();
            UmlType.exportCustomUmlTypes(this, fileNameUri);
        } else if (requestCode == INTENT_OPEN_DOCUMENT_IMPORT_CUSTOM_TYPES && resultCode == RESULT_OK) {
            Uri fileNameUri = data.getData();
            UmlType.importCustomUmlTypes(this, fileNameUri);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            sWriteExternalStoragePermission = grantResults.length > WRITE_EXTERNAL_STORAGE_INDEX &&
                    grantResults[WRITE_EXTERNAL_STORAGE_INDEX] == PackageManager.PERMISSION_GRANTED;
            sReadExternalStoragePermission = grantResults.length > READ_EXTERNAL_STORAGE_INDEX &&
                    grantResults[READ_EXTERNAL_STORAGE_INDEX] == PackageManager.PERMISSION_GRANTED;
        }
    }

    // ====== Project Management Methods ======

    private void saveAs(String projectName) {
        mProject.setName(projectName);
        // updateNavigationView();
        mProject.save(getApplicationContext());
    }

    // ====== Check Permissions ======

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermissions() {
        Log.e("ValenceDebug", "checkPermissions() - START");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String[] permissionString = {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };

                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
                                PackageManager.PERMISSION_GRANTED) {
                    Log.e("ValenceDebug", "Requesting permissions...");
                    requestPermissions(permissionString, REQUEST_PERMISSION);
                } else {
                    Log.e("ValenceDebug", "Permissions already granted");
                }
            }
            Log.e("ValenceDebug", "checkPermissions() - COMPLETED");
        } catch (Exception e) {
            Log.e("ValenceDebug", "checkPermissions() - FAILED", e);
        }
    }
}
