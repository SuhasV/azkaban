package azkaban.flow;

import azkaban.app.JobDescriptor;
import azkaban.app.JobFactory;
import azkaban.app.JobWrappingFactory;
import azkaban.common.jobs.Job;
import org.easymock.IAnswer;
import org.easymock.classextension.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * These tests could probably be simplified by adding a "ThreadFactory" dependency on IndividualJobExecutableFlow
 * 
 * TODO: Maybe do that?
 */
public class IndividualJobExecutableFlowTest
{
    private volatile JobFactory jobFactory;

    private volatile AtomicBoolean assertionViolated;
    private volatile String reason;

    @Before
    public void setUp()
    {
        jobFactory = EasyMock.createMock(JobFactory.class);

        assertionViolated = new AtomicBoolean(false);
        reason = "Default Reason";
    }

    @After
    public void tearDown()
    {
        Assert.assertFalse(reason, assertionViolated.get());
        EasyMock.verify(jobFactory);
    }

    @Test
    public void testSanity() throws Throwable
    {
        final CountDownLatch completionLatch = new CountDownLatch(1);

        final Job mockJob = EasyMock.createMock(Job.class);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);

        EasyMock.expect(jobFactory.factorizeJob()).andReturn(mockJob).once();
        EasyMock.expect(mockJob.getId()).andReturn("success Job").once();

        mockJob.run();
        EasyMock.expectLastCall().andAnswer(new IAnswer<Void>()
        {
            @Override
            public Void answer() throws Throwable
            {
                Assert.assertEquals(Status.RUNNING, executableFlow.getStatus());

                return null;
            }
        }).once();

        EasyMock.replay(mockJob, jobFactory);

        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        executableFlow.execute(new FlowCallback()
        {
            @Override
            public void progressMade()
            {
                assertionViolated.set(true);
                reason = String.format("progressMade() shouldn't actually be called.");
            }

            @Override
            public void completed(Status status)
            {
                completionLatch.countDown();
                if (Status.SUCCEEDED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback: status[%s] != Status.SUCCEEDED", status);
                }
            }
        });

        completionLatch.await(1000, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Status.SUCCEEDED, executableFlow.getStatus());
        Assert.assertEquals(null, executableFlow.getException());

        EasyMock.verify(mockJob);

        Assert.assertTrue("Expected to be able to reset the executableFlow.", executableFlow.reset());
        Assert.assertEquals(Status.READY, executableFlow.getStatus());
    }

    @Test
    public void testFailure() throws Throwable
    {
        final RuntimeException theException = new RuntimeException("Fail!");
        final CountDownLatch completionLatch = new CountDownLatch(1);

        final Job mockJob = EasyMock.createMock(Job.class);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);

        EasyMock.expect(jobFactory.factorizeJob()).andReturn(mockJob).once();
        EasyMock.expect(mockJob.getId()).andReturn("failure Job").once();

        mockJob.run();
        EasyMock.expectLastCall().andAnswer(new IAnswer<Void>()
        {
            @Override
            public Void answer() throws Throwable
            {
                Assert.assertEquals(Status.RUNNING, executableFlow.getStatus());

                throw theException;
            }
        }).once();

        EasyMock.replay(mockJob, jobFactory);

        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        executableFlow.execute(new FlowCallback()
        {
            @Override
            public void progressMade()
            {
                assertionViolated.set(true);
                reason = String.format("progressMade() shouldn't actually be called.");
            }

            @Override
            public void completed(Status status)
            {
                completionLatch.countDown();
                if (Status.FAILED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback: status[%s] != Status.FAILED", status);
                }
            }
        });

        completionLatch.await(1000, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Status.FAILED, executableFlow.getStatus());
        Assert.assertEquals(theException, executableFlow.getException());

        EasyMock.verify(mockJob);

        Assert.assertTrue("Expected to be able to reset the executableFlow.", executableFlow.reset());
        Assert.assertEquals(Status.READY, executableFlow.getStatus());
        Assert.assertEquals(null, executableFlow.getException());
    }

    @Test
    public void testNoChildren() throws Exception
    {
        EasyMock.replay(jobFactory);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);

        Assert.assertFalse("IndividualJobExecutableFlow objects should not have any children.", executableFlow.hasChildren());
        Assert.assertTrue("IndividualJobExecutableFlow objects should not return any children.", executableFlow.getChildren().isEmpty());
    }

    @Test
    public void testAllExecuteCallbacksCalledOnSuccess() throws Throwable
    {
        final CountDownLatch firstCallbackLatch = new CountDownLatch(1);
        final CountDownLatch secondCallbackLatch = new CountDownLatch(1);

        final Job mockJob = EasyMock.createMock(Job.class);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);

        EasyMock.expect(jobFactory.factorizeJob()).andReturn(mockJob).once();
        EasyMock.expect(mockJob.getId()).andReturn("success Job").once();

        mockJob.run();
        EasyMock.expectLastCall().andAnswer(new IAnswer<Void>()
        {
            @Override
            public Void answer() throws Throwable
            {
                Assert.assertEquals(Status.RUNNING, executableFlow.getStatus());

                return null;
            }
        }).once();

        EasyMock.replay(mockJob, jobFactory);

        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        final AtomicBoolean firstCallbackCalled = new AtomicBoolean(false);
        executableFlow.execute(new OneCallFlowCallback(firstCallbackCalled)
        {
            @Override
            public void theCallback(Status status)
            {
                firstCallbackLatch.countDown();
                if (Status.SUCCEEDED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback1: status[%s] != Status.SUCCEEDED", status);
                }
            }
        });

        final AtomicBoolean secondCallbackCalled = new AtomicBoolean(false);
        executableFlow.execute(new OneCallFlowCallback(secondCallbackCalled)
        {
            @Override
            public void theCallback(Status status)
            {
                secondCallbackLatch.countDown();
                if (Status.SUCCEEDED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback2: status[%s] != Status.SUCCEEDED", status);
                }
            }
        });

        firstCallbackLatch.await(1000, TimeUnit.MILLISECONDS);
        secondCallbackLatch.await(1000, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Status.SUCCEEDED, executableFlow.getStatus());
        Assert.assertEquals(null, executableFlow.getException());

        EasyMock.verify(mockJob);

        Assert.assertTrue("First callback wasn't called?", firstCallbackCalled.get());
        Assert.assertTrue("Second callback wasn't called?", secondCallbackCalled.get());
        Assert.assertTrue("Expected to be able to reset the executableFlow.", executableFlow.reset());
        Assert.assertEquals(Status.READY, executableFlow.getStatus());

    }

    @Test
    public void testAllExecuteCallbacksCalledOnFailure() throws Throwable
    {
        final RuntimeException theException = new RuntimeException();
        final CountDownLatch firstCallbackLatch = new CountDownLatch(1);
        final CountDownLatch secondCallbackLatch = new CountDownLatch(1);

        final Job mockJob = EasyMock.createMock(Job.class);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);

        EasyMock.expect(jobFactory.factorizeJob()).andReturn(mockJob).once();
        EasyMock.expect(mockJob.getId()).andReturn("success Job").once();

        mockJob.run();
        EasyMock.expectLastCall().andThrow(theException).once();

        EasyMock.replay(mockJob, jobFactory);

        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        final AtomicBoolean firstCallbackCalled = new AtomicBoolean(false);
        executableFlow.execute(new OneCallFlowCallback(firstCallbackCalled)
        {
            @Override
            public void theCallback(Status status)
            {
                firstCallbackLatch.countDown();
                if (Status.FAILED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback1: status[%s] != Status.FAILED", status);
                }
            }
        });

        final AtomicBoolean secondCallbackCalled = new AtomicBoolean(false);
        executableFlow.execute(new OneCallFlowCallback(secondCallbackCalled)
        {
            @Override
            public void theCallback(Status status)
            {
                secondCallbackLatch.countDown();
                if (Status.FAILED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback2: status[%s] != Status.FAILED", status);
                }
            }
        });

        firstCallbackLatch.await(1000, TimeUnit.MILLISECONDS);
        secondCallbackLatch.await(1000, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Status.FAILED, executableFlow.getStatus());
        Assert.assertEquals(theException, executableFlow.getException());

        EasyMock.verify(mockJob);

        Assert.assertTrue("First callback wasn't called?", firstCallbackCalled.get());
        Assert.assertTrue("Second callback wasn't called?", secondCallbackCalled.get());
        Assert.assertTrue("Expected to be able to reset the executableFlow.", executableFlow.reset());
        Assert.assertEquals(Status.READY, executableFlow.getStatus());
        Assert.assertEquals(null, executableFlow.getException());

    }

    @Test
    public void testReset() throws Exception
    {
        final CountDownLatch completionLatch = new CountDownLatch(1);

        final Job mockJob = EasyMock.createMock(Job.class);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);

        EasyMock.expect(jobFactory.factorizeJob()).andReturn(mockJob).once();
        EasyMock.expect(mockJob.getId()).andReturn("success Job").once();

        mockJob.run();
        EasyMock.expectLastCall().andAnswer(new IAnswer<Void>()
        {
            @Override
            public Void answer() throws Throwable
            {
                Assert.assertEquals(Status.RUNNING, executableFlow.getStatus());

                return null;
            }
        }).once();

        EasyMock.replay(mockJob, jobFactory);

        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        executableFlow.execute(new FlowCallback()
        {
            @Override
            public void progressMade()
            {
                assertionViolated.set(true);
                reason = String.format("progressMade() shouldn't actually be called.");
            }

            @Override
            public void completed(Status status)
            {
                completionLatch.countDown();
                if (Status.SUCCEEDED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback: status[%s] != Status.SUCCEEDED", status);
                }
            }
        });

        completionLatch.await(1000, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Status.SUCCEEDED, executableFlow.getStatus());
        Assert.assertEquals(null, executableFlow.getException());

        EasyMock.verify(mockJob, jobFactory);
        EasyMock.reset(mockJob, jobFactory);

        final CountDownLatch completionLatch2 = new CountDownLatch(1);

        Assert.assertTrue("Expected to be able to reset the executableFlow.", executableFlow.reset());
        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        EasyMock.expect(jobFactory.factorizeJob()).andReturn(mockJob).once();
        EasyMock.expect(mockJob.getId()).andReturn("success Job").once();

        mockJob.run();
        EasyMock.expectLastCall().andAnswer(new IAnswer<Void>()
        {
            @Override
            public Void answer() throws Throwable
            {
                Assert.assertEquals(Status.RUNNING, executableFlow.getStatus());

                return null;
            }
        }).once();

        EasyMock.replay(mockJob, jobFactory);

        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        executableFlow.execute(new FlowCallback()
        {
            @Override
            public void progressMade()
            {
                assertionViolated.set(true);
                reason = String.format("progressMade() shouldn't actually be called.");
            }

            @Override
            public void completed(Status status)
            {
                completionLatch2.countDown();
                if (Status.SUCCEEDED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback: status[%s] != Status.SUCCEEDED", status);
                }
            }
        });

        completionLatch2.await(1000, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Status.SUCCEEDED, executableFlow.getStatus());
        Assert.assertEquals(null, executableFlow.getException());

        EasyMock.verify(mockJob);
    }

    @Test
    public void testResetWithFailedJob() throws Exception
    {
        final CountDownLatch completionLatch = new CountDownLatch(1);

        final Job mockJob = EasyMock.createMock(Job.class);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);
        executableFlow.setStatus(Status.FAILED);

        Assert.assertTrue("Should be able to reset the flow.", executableFlow.reset());

        EasyMock.expect(jobFactory.factorizeJob()).andReturn(mockJob).once();
        EasyMock.expect(mockJob.getId()).andReturn("success Job").once();

        mockJob.run();
        EasyMock.expectLastCall().andAnswer(new IAnswer<Void>()
        {
            @Override
            public Void answer() throws Throwable
            {
                Assert.assertEquals(Status.RUNNING, executableFlow.getStatus());

                return null;
            }
        }).once();

        EasyMock.replay(mockJob, jobFactory);

        Assert.assertEquals(Status.READY, executableFlow.getStatus());

        executableFlow.execute(new FlowCallback()
        {
            @Override
            public void progressMade()
            {
                assertionViolated.set(true);
                reason = String.format("progressMade() shouldn't actually be called.");
            }

            @Override
            public void completed(Status status)
            {
                completionLatch.countDown();
                if (Status.SUCCEEDED != status) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow Callback: status[%s] != Status.SUCCEEDED", status);
                }
            }
        });

        completionLatch.await(1000, TimeUnit.MILLISECONDS);
        Assert.assertEquals(Status.SUCCEEDED, executableFlow.getStatus());

        EasyMock.verify(mockJob);
    }

    @Test
    public void testCancel() throws Exception
    {
        final CountDownLatch cancelLatch = new CountDownLatch(1);
        final CountDownLatch runLatch = new CountDownLatch(1);

        final Job mockJob = EasyMock.createMock(Job.class);
        final IndividualJobExecutableFlow executableFlow = new IndividualJobExecutableFlow("blah", "blah", jobFactory);

        Assert.assertTrue("Should be able to reset the flow.", executableFlow.reset());

        EasyMock.expect(mockJob.getId()).andReturn("blah").once();
        mockJob.run();
        EasyMock.expect(jobFactory.factorizeJob()).andAnswer(new IAnswer<Job>()
        {
            @Override
            public Job answer() throws Throwable
            {
                cancelLatch.countDown();
                runLatch.await();

                return mockJob;
            }
        }).once();

        EasyMock.replay(mockJob, jobFactory);

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    cancelLatch.await();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if(! executableFlow.cancel()) {
                    assertionViolated.set(true);
                    reason = "In cancel thread: call to cancel returned false.";
                }

                runLatch.countDown();
            }
        }).start();

        AtomicBoolean runOnce = new AtomicBoolean(false);
        executableFlow.execute(new OneCallFlowCallback(runOnce)
        {
            @Override
            protected void theCallback(Status status)
            {
                if (status != Status.FAILED) {
                    assertionViolated.set(true);
                    reason = String.format("In executableFlow callback: status[%s] != Status.FAILED", status);
                    return;
                }

                if (! (runLatch.getCount() == 1 && cancelLatch.getCount() == 0)) {
                    assertionViolated.set(true);
                    reason = String.format(
                            "In executableFlow callback: ! (runLatch.count[%s] == 1 && cancelLatch.count[%s] == 0)",
                            runLatch.getCount(),
                            cancelLatch.getCount()
                    );
                }
            }
        });

        Assert.assertTrue("Expected callback to be called once.", runOnce.get());
    }
}