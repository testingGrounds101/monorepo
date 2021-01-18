package edu.nyu.classes.nyugrades.api;

public class Grade
{
    public String netId;
    public String emplId;
    public String gradeletter;


    public Grade(String netId, String emplId, String gradeletter) {
        this.netId = netId;
        this.emplId = emplId;
        this.gradeletter = gradeletter;
    }
}
