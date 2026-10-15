package cn.iocoder.wx.module.dimensions.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class AssociationObject {
    private String valueName;

    private String labelName;

    private List<AssociationObject> children;

    private boolean isLeaf;

    public AssociationObject(){
        this.valueName ="";
        this.labelName="";
        this.children=new ArrayList<>();
        this.isLeaf = false;
    }


}
