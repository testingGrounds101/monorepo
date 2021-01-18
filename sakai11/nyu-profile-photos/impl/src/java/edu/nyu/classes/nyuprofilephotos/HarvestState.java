package edu.nyu.classes.nyuprofilephotos;

import java.io.*;
import java.util.*;

import org.sakaiproject.component.cover.HotReloadConfigurationService;


class HarvestState {
    private final String stateFile;

    public HarvestState() {
        this.stateFile = HotReloadConfigurationService.getString("profile-photos.state-file", "");

        if (this.stateFile.isEmpty()) {
            throw new RuntimeException("'profile-photos.state-file' must be set to a writable file");
        }
    }

    public Date getLastRunDate() throws Exception {
        if (!new File(this.stateFile).exists()) {
            return null;
        }

        try (BufferedReader in = new BufferedReader(new FileReader(this.stateFile))) {
            String s = in.readLine();
            return new Date(Long.valueOf(s));
        }
    }

    public void storeLastRunDate(Date date) throws Exception {
        String tmpPath = this.stateFile + ".tmp";

        try (PrintWriter out = new PrintWriter(tmpPath)) {
            out.println(String.valueOf(date.getTime()));
        }

        new File(tmpPath).renameTo(new File(this.stateFile));
    }
}
