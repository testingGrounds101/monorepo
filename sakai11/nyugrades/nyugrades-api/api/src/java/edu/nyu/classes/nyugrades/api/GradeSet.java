package edu.nyu.classes.nyugrades.api;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class GradeSet implements Iterable<Grade>
{
    private Iterable<Grade> grades;

    public GradeSet(Iterable<Grade> grades)
    {
        this.grades = grades;
    }

    public Iterator<Grade> iterator()
    {
        return grades.iterator();
    }
}
