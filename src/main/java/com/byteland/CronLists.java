package com.byteland;

import com.byteland.model.Cron;
import com.byteland.model.DefaultCron;
import com.google.gson.Gson;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CronLists {

    private static final Logger LOGGER = Logger.getLogger(CronLists.class.getName());
    ObservableList<Cron> cronObservableList = FXCollections.observableArrayList();

    public CronLists() { setCronList(CronUtils.getCronFileList()); }

    private void setCronList(List<File> files) {
        Gson gson;
        DefaultCron defaultCron;

        for (File file : files) {
            gson = new Gson();
            defaultCron = new DefaultCron();

            try {
                defaultCron = gson.fromJson(
                        IOUtils.toString(file.toURI(), Charset.defaultCharset())
                        , DefaultCron.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            cronObservableList.add(defaultCron.getCron());
        }

        if(files.size() == 0)
            cronObservableList.add(0, getDefaultCron());

        LOGGER.log(Level.FINEST,"cronObservableList: " + cronObservableList.sorted());
        LOGGER.log(Level.INFO, "Total crons: " + cronObservableList.size());
    }

    private Cron getDefaultCron() {
        Gson gson = new Gson();
        DefaultCron defaultCron = new DefaultCron();

        try {
            defaultCron = gson.fromJson(
                    IOUtils.toString(getClass().getResourceAsStream("/json/sample_cron.json")
                            , Charset.defaultCharset())
                    ,   DefaultCron.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return defaultCron.getCron();
    }

    Cron getCronById(int cronId) {
        return cronObservableList.stream()
                .filter(cron -> cron.getId() == cronId)
                .findFirst()
                .orElse(null);
    }
}
