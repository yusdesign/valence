package com.yusdesign.valence.controller;

import androidx.fragment.app.Fragment;

import com.yusdesign.valence.model.UmlProject;

//    **********************************************************************************************
//    Callback interface
//    **********************************************************************************************
    public interface FragmentObserver {


        void setPurpose(Purpose purpose);
        UmlProject getProject();
        void closeClassEditorFragment(Fragment fragment);
        void closeAttributeEditorFragment(Fragment fragment);
        void closeMethodEditorFragment(Fragment fragment);
        void closeParameterEditorFragment(Fragment fragment);
        void openAttributeEditorFragment(int attributeIndex,int classIndex);
        void openMethodEditorFragment(int methodIndex,int classIndex);
        void openParameterEditorFragment(int parameterIndex,int methodIndex,int classIndex);

        enum Purpose{NONE,CREATE_CLASS,EDIT_CLASS,CREATE_ATTRIBUTE,EDIT_ATTRIBUTE,CREATE_METHOD,EDIT_METHOD,CREATE_PARAMETER,EDIT_PARAMETER}
}
