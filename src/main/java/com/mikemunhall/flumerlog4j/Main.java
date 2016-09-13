package com.mikemunhall.flumerlog4j;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Random;

public class Main {

    public static void main(String args[]) throws InterruptedException {

        Random random = new Random();
        Logger logger = LogManager.getLogger(Main.class.getName());

        while (true) {
            logger.info("next int: {}\n        line two: next int: {}", random.nextInt(), random.nextInt());
            Thread.sleep(1000);
        }
    }
}
