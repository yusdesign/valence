package com.yusdesign.valence.module;

public class Version implements Comparable<Version> {
    private final int major;
    private final int minor;
    private final int patch;
    
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
