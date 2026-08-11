package com.yusdesign.valence.gedcom;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.yusdesign.valence.module.UmlModule;
import com.yusdesign.valence.module.Version;
import com.yusdesign.valence.model.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Simple GEDCOM 5.5.1 and 7.0.0 parser.
 * Converts GEDCOM data to Valence UML model.
 */
public class GedcomDataProvider implements UmlModule.UmlDataProvider {
    private static final String TAG = "GedcomProvider";
    public static final Version VERSION = new Version(1, 0, 0);
    
    @Override
    public UmlProject loadData(Context context, Uri fileUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) return null;
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            List<GedcomRecord> records = new ArrayList<>();
            
            // Parse GEDCOM lines into records
            GedcomRecord currentRecord = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // Simple parse: 0 @I123@ INDI
                if (line.startsWith("0 ")) {
                    if (currentRecord != null) {
                        records.add(currentRecord);
                    }
                    currentRecord = parseRecord(line);
                } else if (currentRecord != null && line.startsWith("1 ")) {
                    // Parse attribute
                    currentRecord.addAttribute(line.substring(2));
                } else if (currentRecord != null && line.startsWith("2 ")) {
                    // Sub-attribute
                    currentRecord.addSubAttribute(line.substring(2));
                }
            }
            if (currentRecord != null) {
                records.add(currentRecord);
            }
            
            // Convert to UmlProject
            return convertToUmlProject(context, records);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse GEDCOM file", e);
            return null;
        }
    }
    
    @Override
    public void saveData(Context context, Uri fileUri, UmlProject project) {
        // Not implemented yet - exporting UML to GEDCOM
        Log.i(TAG, "Save to GEDCOM not yet implemented");
    }
    
    @Override
    public String getProviderName() { return "GEDCOM 5.5.1/7.0.0 Parser"; }
    
    @Override
    public String[] getSupportedFileExtensions() { return new String[]{".ged", ".GED"}; }
    
    @Override
    public Version getVersion() { return VERSION; }
    
    // Internal parser classes
    private static class GedcomRecord {
        String id;
        String type;
        Map<String, String> attributes = new HashMap<>();
        Map<String, List<String>> subAttributes = new HashMap<>();
        
        GedcomRecord(String id, String type) {
            this.id = id;
            this.type = type;
        }
        
        void addAttribute(String attribute) {
            String[] parts = attribute.split(" ", 2);
            if (parts.length == 2) {
                attributes.put(parts[0], parts[1]);
            }
        }
        
        void addSubAttribute(String attribute) {
            String[] parts = attribute.split(" ", 2);
            if (parts.length == 2) {
                subAttributes.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
            }
        }
    }
    
    private GedcomRecord parseRecord(String line) {
        String[] parts = line.substring(2).trim().split(" ", 2);
        if (parts.length == 2) {
            String id = parts[0]; // @I123@
            String type = parts[1]; // INDI, FAM, etc.
            return new GedcomRecord(id, type);
        }
        return new GedcomRecord("", "UNKNOWN");
    }
    
    private UmlProject convertToUmlProject(Context context, List<GedcomRecord> records) {
        UmlProject project = new UmlProject("Imported GEDCOM", context);
        
        // Maps to store created classes for relationships
        Map<String, UmlClass> individuals = new HashMap<>();
        Map<String, UmlClass> families = new HashMap<>();
        
        for (GedcomRecord record : records) {
            if ("INDI".equals(record.type)) {
                UmlClass individual = createIndividualClass(record);
                project.addUmlClass(individual);
                individuals.put(record.id, individual);
            } else if ("FAM".equals(record.type)) {
                UmlClass family = createFamilyClass(record);
                project.addUmlClass(family);
                families.put(record.id, family);
            }
        }
        
        // Create relationships
        for (GedcomRecord record : records) {
            if ("FAM".equals(record.type)) {
                String husbandId = record.attributes.get("HUSB");
                String wifeId = record.attributes.get("WIFE");
                List<String> childrenIds = record.subAttributes.getOrDefault("CHIL", new ArrayList<>());
                
                UmlClass husband = individuals.get(husbandId);
                UmlClass wife = individuals.get(wifeId);
                
                if (husband != null && wife != null) {
                    // Create spousal relationship
                    UmlRelation marriage = new UmlRelation(husband, wife, UmlRelation.UmlRelationType.ASSOCIATION);
                    project.addUmlRelation(marriage);
                }
                
                // Create parent-child relationships
                for (String childId : childrenIds) {
                    UmlClass child = individuals.get(childId);
                    if (husband != null && child != null) {
                        UmlRelation parentChild = new UmlRelation(husband, child, UmlRelation.UmlRelationType.INHERITANCE);
                        project.addUmlRelation(parentChild);
                    }
                }
            }
        }
        
        return project;
    }
    
    private UmlClass createIndividualClass(GedcomRecord record) {
        String name = record.attributes.getOrDefault("NAME", "Individual");
        UmlClass umlClass = new UmlClass(name, UmlClass.UmlClassType.JAVA_CLASS);
        
        // Add attributes as class attributes
        for (Map.Entry<String, String> attr : record.attributes.entrySet()) {
            String attrName = attr.getKey().toLowerCase();
            String attrValue = attr.getValue();
            
            // Skip name as it's used for class name
            if ("NAME".equals(attr.getKey())) continue;
            
            UmlClassAttribute attribute = new UmlClassAttribute(
                attrName, 
                "String", 
                0, 
                UmlClassAttribute.AccessModifier.PRIVATE, 
                false
            );
            // Store value as a comment or additional info
            umlClass.addAttribute(attribute);
        }
        
        // Add sub-attributes as methods or additional fields
        if (!record.subAttributes.isEmpty()) {
            for (Map.Entry<String, List<String>> subAttr : record.subAttributes.entrySet()) {
                String methodName = "get" + subAttr.getKey().substring(0, 1).toUpperCase() + subAttr.getKey().substring(1).toLowerCase();
                UmlClassMethod method = new UmlClassMethod(
                    methodName,
                    "String",
                    0,
                    UmlClassMethod.AccessModifier.PUBLIC,
                    false,
                    false
                );
                // Add parameters if needed
                // method.addParameter(new UmlClassMethodParameter(...));
                umlClass.addMethod(method);
            }
        }
        
        return umlClass;
    }
    
    private UmlClass createFamilyClass(GedcomRecord record) {
        String name = "Family " + record.id;
        UmlClass umlClass = new UmlClass(name, UmlClass.UmlClassType.JAVA_CLASS);
        
        // Add family-specific attributes
        for (Map.Entry<String, String> attr : record.attributes.entrySet()) {
            String attrName = attr.getKey().toLowerCase();
            UmlClassAttribute attribute = new UmlClassAttribute(
                attrName, 
                "String", 
                0, 
                UmlClassAttribute.AccessModifier.PRIVATE, 
                false
            );
            umlClass.addAttribute(attribute);
        }
        
        return umlClass;
    }
}
