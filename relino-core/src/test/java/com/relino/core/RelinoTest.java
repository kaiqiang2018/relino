package com.relino.core;

import com.relino.core.db.Store;
import com.relino.core.helper.TestHelper;
import com.relino.core.model.Job;
import com.relino.core.model.JobAttr;
import com.relino.core.model.Oper;
import com.relino.core.model.executequeue.ExecuteQueue;
import com.relino.core.model.executequeue.PessimisticLockExecuteQueue;
import com.relino.core.support.id.IdGenerator;
import com.relino.core.support.id.UUIDIdGenerator;
import com.relino.core.support.thread.QueueSizeLimitExecutor;
import com.relino.core.task.DeadJobWatchDog;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ThreadLocalRandom;

public class RelinoTest {

    private static final Logger log = LoggerFactory.getLogger(RelinoTest.class);

    private Relino app;

    @Before
    public void setUp() {
        TestHelper.testBootStrap();
        Store store = TestHelper.getStore();
        IdGenerator idGenerator = new UUIDIdGenerator();
        ExecuteQueue executeQueue = new PessimisticLockExecuteQueue(store);
        QueueSizeLimitExecutor<Job> jotExecutor = new QueueSizeLimitExecutor<>("job", 5, 20, 3000);

        Job.setStore(store);

        app = new Relino(store, idGenerator, jotExecutor, executeQueue, 100, 100);
    }

    @Test
    public void test() throws InterruptedException, IOException {
        // 一个线程不断提交job
        JobProducer jobProducer = app.jobProducer;

        for (int i = 0; i < 50; i++) {
            new Thread(() -> {
                for (int count = 0; count < 100; count++) {
                    Oper mOper = Oper.builder(TestHelper.SleepAndLogAction_ID).maxExecuteCount(5).build();
                    JobAttr initAttr = new JobAttr();
                    initAttr.setLong("sleepTime", 10);
                    Job job = jobProducer.builder(mOper).commonAttr(initAttr).delayJob(10 + ThreadLocalRandom.current().nextInt(50)).build();
                    jobProducer.createJob(job);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        log.error("error ", e);
                    }
                }
            }).start();
        }

        System.out.println("Press enter/return to quit\n");
        new BufferedReader(new InputStreamReader(System.in)).readLine();

        log.info("end .... ");
    }

    @Test
    public void testDeadJobWatchDog() {
        DeadJobWatchDog watchDog = new DeadJobWatchDog(app.store, 1);
        watchDog.execute();
    }
}