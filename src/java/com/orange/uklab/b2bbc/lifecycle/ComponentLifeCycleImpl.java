/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.orange.uklab.b2bbc.lifecycle;

import com.orange.uklab.b2bbc.descriptor.impl.ServiceUnitImpl;
import com.orange.uklab.b2bbc.persistence.PersistenceException;
import com.orange.uklab.b2bbc.persistence.impl.DataPersisterImpl;
import com.orange.uklab.b2bbc.runtime.contexts.RuntimeComponentContext;
import com.orange.uklab.b2bbc.runtime.contexts.impl.StorageException;
import gov.nist.javax.sip.SipStackImpl;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jbi.JBIException;
import javax.jbi.component.ComponentContext;
import javax.jbi.component.ComponentLifeCycle;
import javax.management.ObjectName;
import javax.sip.PeerUnavailableException;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipStack;

/**
 *
 * @author hasaneinali
 */
public class ComponentLifeCycleImpl implements ComponentLifeCycle
{
    private Logger logger = null;
    private ComponentContext componentContext = null;
    private String logPreamble = "@@@@@@@@@@@@@@@@@@@@@:";
    private boolean isSipStackStarted = false;

    /**
     * 
     */
    private void getLogger()
    {
        try
        {
            this.logger = componentContext.getLogger(this.getClass().getName(), null);
        }
        catch (MissingResourceException ex)
        {
            Logger.getLogger(ComponentLifeCycleImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (JBIException ex)
        {
            Logger.getLogger(ComponentLifeCycleImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * 
     * @return
     */
    @Override
    public ObjectName getExtensionMBeanName()
    {
        /**
         * If there is an extension MBean that will help creating new
         * states for the Component, then return it, otherwise return
         * null.
         */
        return null;
    }

    /**
     * 
     * @param componentContext
     * @throws JBIException
     */
    @Override
    public void init(ComponentContext componentContext) throws JBIException
    {
        /**
         * Store a copy of the passed in ComponentContext
         * into a globcal private variable for later use by other parts
         * of the class.
         */
        this.componentContext = componentContext;
        getLogger();
        logger.info(logPreamble + this.getClass().getName() + ": init()");
        /**
         * Now we need to initialize the RuntimeComponentContext that will be
         * available anywhere inside the component during the component life
         * cycle.
         */
        try
        {
            logger.info("Initializing the RuntimeComponentContext started...");
            initRuntimeComponentContext();
            logger.info("Initializing the RuntimeComponentContext completed...");
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            throw new JBIException("Error initializing the RuntimeComponentContext caused by: " + ex.getMessage());
        }
        logger.info(logPreamble + this.getClass().getName() + ": init()" + "RuntimeComponentContext has been initialized...");
    }

    private void initRuntimeComponentContext() throws PersistenceException, StorageException, PeerUnavailableException
    {        
        /**
         * Setting the ComponentContext in the RuntimeComponentContext
         * This can only be done once in a Component life time.
         */
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        runtimeComponentContext.setComponentContext(componentContext);
        logger.info("Initializing the RuntimeComponentContext: Setting the ComponentContext Completed");
        /**
         * Retrieve the Service Units persisted on disk and set the RuntimeComponentContext
         * Accordingly.
         */
        DataPersisterImpl dataPersisterImpl = new DataPersisterImpl();
        logger.info("HHHHHHHHHHHHHHHHHHHHH (1)");
        Object persistedObjects[] = dataPersisterImpl.retrieveAllObjects("ServiceUnits");
        logger.info("HHHHHHHHHHHHHHHHHHHHH (2)");
                                                       
        if(persistedObjects != null)
        {
            ServiceUnitImpl[] serviceUnits = new ServiceUnitImpl[persistedObjects.length];
            for(int i = 0 ; i < persistedObjects.length ; i++)
            {
                serviceUnits[i] = (ServiceUnitImpl)persistedObjects[i];
            }
            runtimeComponentContext.storeServiceUnits(serviceUnits);
            logger.info("HHHHHHHHHHHHHHHHHHHHH (3)");
            logger.info("Initializing the RuntimeComponentContext: Setting the ServiceUnits completed: " + serviceUnits.length + " service units found.");
        }
        else
        {
            logger.info("Initializing the RuntimeComponentContext: Setting the ServiceUnits completed: 0 service units found.");
        }

        /**
         * Here we need to obtain an instance of a SipStack and set store
         * a refreence to it in the ComponentRuntimeContext
         */
        logger.info("Initializing the RuntimeComponentContext: Setting the SipFactory...");
        SipFactory sipFactory = SipFactory.getInstance();
        /**
         * SipStack configurations are done via a properties file, this is
         * implemented as a java class at the moment but we can change the
         * implementation at anytime and expose them in a java.properties
         * file that is configurable without the need to change the code.
         */
        Properties sipConfig = new Properties();
        sipConfig.setProperty("javax.sip.STACK_NAME", "com.orange.uklab.B2BComponentSipStack");
        sipConfig.setProperty("gov.nist.javax.sip.SERVER_LOGGER", "com.orange.uklab.b2bbc.sipStack.SipServerLoggerImpl");
        sipConfig.setProperty("gov.nist.javax.sip.STACK_LOGGER", "com.orange.uklab.b2bbc.sipStack.SipStackLoggerImpl");        
        sipConfig.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "ON");       
        sipFactory.setPathName("gov.nist");
        logger.info("Initializing the RuntimeComponentContext: Setting the SipStack...");
        SipStack sipStack = sipFactory.createSipStack(sipConfig);        
        runtimeComponentContext.setSipFactory(sipFactory);
        logger.info("Initializing the RuntimeComponentContext: The SipFactory has been set...");
        runtimeComponentContext.setSipStack(sipStack);
        logger.info("Initializing the RuntimeComponentContext: The SipStack has been set...");
    }
    /**
     * 
     * @throws JBIException
     */
    @Override
    public void shutDown() throws JBIException
    {
        logger.info(logPreamble + this.getClass().getName() + ": shutdown()");        
        /**
         * During the component shutdown, we need to dump all the ServiceUnit
         * stored in the RuntimeComponentContext into disk, ther persistence
         * storage. The persistence storage is stale at the moment so it
         * need to be updated with the new information currently residing
         * in memory, before the component get shutted down.
         */
        logger.info("Persisting the RuntimeComponentContext started...");
        persistRuntimeContext();
        clearRuntimeComponentContext();
        logger.info("Persisting the RuntimeComponentContext completed...");        
    }

    private void clearRuntimeComponentContext()
    {
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        runtimeComponentContext.clear();
    }

    private void persistRuntimeContext()
    {
        /**
         * The ComponentRuntimeContext does not support getting all the contained
         * service units at the moment so we need to develop that functionality
         * first.
         */
        try
        {
            RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
            ServiceUnitImpl serviceUnit[] = (ServiceUnitImpl[]) runtimeComponentContext.getAllServiceUnits();
            DataPersisterImpl dataPersisterImpl = new DataPersisterImpl();
            logger.info("Persisting the RuntimeComponentContext: Persisting " + serviceUnit.length + " service units to disk storage.");
            for(ServiceUnitImpl s : serviceUnit)
            {
                dataPersisterImpl.persistObject(s, "ServiceUnits", true);
            }
            logger.info("Completing the persistence operation...");
        }
        catch(Exception ex)
        {
            logger.info(ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 
     * @throws JBIException
     */
    @Override
    public void start() throws JBIException
    {       
        logger.info(logPreamble + this.getClass().getName() + ": start()");
        if(!isSipStackStarted)
        {
            logger.info(this.getClass().getName() + ".start(): Starting the SIP Stack...");
            startSipStack();            
            logger.info(this.getClass().getName() + ".start(): SipStack has been started");
        }
    }

    private void startSipStack()
    {
        SipStackImpl sipStack = (SipStackImpl) RuntimeComponentContext.getInstance().getSipStack();
        try
        {
            logger.info(this.getClass().getName() + ".startSipStack(): Starting the SIP Stack..");            
            sipStack.start();
            isSipStackStarted = true;
            logger.info(this.getClass().getName() + ".startSipStack(): SipStack has been started...");
        }
        catch (SipException ex)
        {
            logger.info(this.getClass().getName() + ".startSipStack(): Exception during starting up the SipStack");
            Logger.getLogger(ComponentLifeCycleImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private void stopSipStack()
    {
        SipStack sipStack = RuntimeComponentContext.getInstance().getSipStack();
        logger.info(this.getClass().getName() + ".stopSipStack(): Stopping the SIP Stack..");
        sipStack.stop();
        isSipStackStarted = false;
        logger.info(this.getClass().getName() + ".stopSipStack(): SipStack has been stopped...");
    }

    /**
     * 
     * @throws JBIException
     */
    @Override
    public void stop() throws JBIException
    {
        logger.info(logPreamble + this.getClass().getName() + ": stop()");
        /**
         * Shutdown the SipStack
         */
        if(isSipStackStarted)
        {
            logger.info(this.getClass().getName() + ".stop(): Stopping the SIP Stack...");
            stopSipStack();
            logger.info(this.getClass().getName() + ".stop(): Stopping the SIP Stack...");
        }
    }

}
