/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.orange.uklab.b2bbc.lifecycle;

import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;
import com.orange.uklab.b2bbc.descriptor.AbstractServiceEndpoint;
import com.orange.uklab.b2bbc.descriptor.ServiceUnit;
import com.orange.uklab.b2bbc.descriptor.impl.JbiDescriptor;
import com.orange.uklab.b2bbc.descriptor.impl.ServiceEndpointImpl;
import com.orange.uklab.b2bbc.descriptor.impl.ServiceUnitImpl;
import com.orange.uklab.b2bbc.main.B2BException;
import com.orange.uklab.b2bbc.nmr.impl.NmrAgentImpl;
import com.orange.uklab.b2bbc.nmr.impl.NmrListenerImpl;
import com.orange.uklab.b2bbc.persistence.PersistenceException;
import com.orange.uklab.b2bbc.persistence.impl.DataPersisterImpl;
import com.orange.uklab.b2bbc.runtime.contexts.RuntimeComponentContext;
import com.orange.uklab.b2bbc.runtime.contexts.impl.ServiceUnitStore;
import com.orange.uklab.b2bbc.runtime.contexts.impl.StorageException;
import com.orange.uklab.b2bbc.sip.impl.SipListenerImpl;
import com.orange.uklab.b2bbc.utils.ComponentTaskResultFactory;
import com.orange.uklab.b2bbc.utils.ExceptionInfo;
import com.orange.uklab.b2bbc.utils.TaskStatusMessage;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jbi.JBIException;
import javax.jbi.component.ServiceUnitManager;
import javax.jbi.management.DeploymentException;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.sip.ListeningPoint;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;

/**
 *
 * @author hasaneinali
 */
public class ServiceUnitManagerImpl implements ServiceUnitManager
{
    private Logger logger;    
    /**
     * This methos will be called by the JBI framework when deploying a service artifact
     * to the component that used to retrieve this ServiceUnitManager.
     * If the deployment process is successfull, then we should return back a
     * ComponentTaskResult XML String constructed using the ComponentTaskResultFactory
     * class that is created for such purpose. The returned String represents the
     * outcome of the deployment operation.
     * @param serviceUnitName
     * @param serviceUnitRootPath
     * @return
     * @throws DeploymentException
     */
    @Override
    public String deploy(String serviceUnitName, String serviceUnitRootPath) throws DeploymentException
    {
        /**
         * Initialize the logger
         */
        initLogger();
        /**
         * Persist the deployed Service Unit to disk storage using the
         * DataPersister. But before that we need to parse the deployment
         * descriptor into a Java Object of ServiceUnit using the JbiDescriptor
         */
        String componentTaskResult = null;
        try
        {
            componentTaskResult = deployServiceUnit(serviceUnitName, serviceUnitRootPath);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            throw new DeploymentException(ex.getMessage());
        }
        return componentTaskResult;
    }

    /**
     * This method handle the deployment operation of the service unit, it persist
     * the Service Unit being deployed into disk and store a copy of it in the
     * RuntimeComponentContext, then upon a successful completion of those steps
     * it constructs a ComponentTaskResult to be returned back to the caller
     * subroutine.
     * @param serviceUnitName
     * @param serviceUnitRootPath
     * @return
     */
    private String deployServiceUnit(String serviceUnitName, String serviceUnitRootPath) throws Exception
    {
        JbiDescriptor jbiDescriptor = new JbiDescriptor(serviceUnitRootPath, serviceUnitName, JbiDescriptor.JBI_DESCRIPTOR_TYPE_SERVICE_UNIT);
        ServiceUnit serviceUnit = jbiDescriptor.parseServiceUnitDescriptor();        

        /**
         * Persist the ServiceUnit on Disk
         */
        boolean persistenceResult = false;
        persistenceResult = persistServiceUnit(serviceUnit);

        /**
         * Store the ServiceUnit in the RuntimeComponentContext
         */
        boolean runtimeStorageResult = false;
        runtimeStorageResult = contexualizeServiceUnit(serviceUnit);

        /**
         * Generate a ComponentTaskResult accordingly, depending on the
         * outcome of the above operations.
         */
        String resultMessage = generateComponentTaskResult(persistenceResult, runtimeStorageResult, serviceUnit);
        return resultMessage;
    }

    private String generateComponentTaskResult(boolean persistenceResult, boolean runtimeStorageResult, ServiceUnit serviceUnit)
    {
        TaskStatusMessage exceptionInfoTaskStatusMsg = new TaskStatusMessage(serviceUnit.getName() + "DeploymentLocToken01", serviceUnit.getName() + "MyLocMessage01", new String[] {serviceUnit.getName() + "P1", serviceUnit.getName() + "P2"});
        ExceptionInfo exceptionInfo = new ExceptionInfo(1, exceptionInfoTaskStatusMsg, "stack trace is not set");
        TaskStatusMessage taskStatusMessage = new TaskStatusMessage(serviceUnit.getName() + "DeploymentLocToken02", serviceUnit.getName() + "MyLocMessage02", new String[] {serviceUnit.getName() + "P11", serviceUnit.getName() + "P21"});
        ComponentTaskResultFactory componentTaskResultFactory = new ComponentTaskResultFactory(RuntimeComponentContext.getInstance().getComponentName());

        String resultMessage = null;
        try
        {
            String exitCode = ((persistenceResult == true) && (runtimeStorageResult == true)) ? ComponentTaskResultFactory.TASK_RESULT_SUCCESS : ComponentTaskResultFactory.TASK_RESULT_FAILED;
            resultMessage = componentTaskResultFactory.createMessage(serviceUnit.getName() + "DeploymentTask", exitCode, ComponentTaskResultFactory.MESSAGE_TYPE_INFO, new TaskStatusMessage[]{taskStatusMessage}, new ExceptionInfo[]{exceptionInfo});
        }
        catch (B2BException ex)
        {
            logger.info(ex.getMessage());
            ex.printStackTrace();
        }
        return resultMessage;
    }

    private boolean contexualizeServiceUnit(ServiceUnit serviceUnit)
    {
        boolean runtimeStorageResult = false;
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        try
        {
            runtimeStorageResult = runtimeComponentContext.storeServiceUnit(serviceUnit);
            logger.info("Service Unit: " + serviceUnit.getName() + " has been stored in the RuntimeComponentContext");
        }
        catch (StorageException ex)
        {
             logger.info(ex.getMessage());
             ex.printStackTrace();
        }
        return runtimeStorageResult;
    }
    
    private boolean persistServiceUnit(ServiceUnit serviceUnit)
    {
        boolean persistenceResult = false;
        logger.info("Service Unit: " + serviceUnit.getName() + " has been parsed");
        DataPersisterImpl dataPersister = new DataPersisterImpl();
        try
        {
            if(!dataPersister.isCategoryExist("ServiceUnits"))
            {
                dataPersister.createPersistenceCategory("ServiceUnits");
                logger.info("Service Unit: " + serviceUnit.getName() + " has been persisted");
            }
            persistenceResult = dataPersister.persistObject(serviceUnit, "ServiceUnits", true);
        }
        catch (PersistenceException ex)
        {
            logger.info(ex.getMessage());
            ex.printStackTrace();
        }
        return persistenceResult;
    }

    private void initLogger()
    {
        this.logger = RuntimeComponentContext.getInstance().getLogger(ServiceUnitManagerImpl.class.getName(), null);
    }
    /**
     * 
     * @param arg0
     * @param arg1
     * @throws DeploymentException
     */
    @Override
    public void init(String serviceUnitName, String serviceUnitRootPath) throws DeploymentException
    {        
        initLogger();
        logger.info("Initializing the ServiceUnitManager");
        /**
         * Setting the Service Unit status in the RuntimeComponentContext to STOPPED
         * as per the JBI specifications.
         * The init() method might also be called by the JBI implementation when
         * bringing the component back to its previous SHUTDOWN state after
         * a component reboot/crash. Then followed by e shutdown() method call.
         */
        setServiceUnitStatus(serviceUnitName, ServiceUnitStore.SERVICE_UNIT_STATUS_STOPPED);
        logger.info(serviceUnitName + " has been initialized and its status set to STOPPED");
    }

    /**
     * 
     * @param arg0
     * @throws DeploymentException
     */
    @Override
    public void start(String serviceUnitName) throws DeploymentException
    {
        logger.info("Staring Service Unit named: " + serviceUnitName);
        /**
         * We need here to obtain a SipProvider instance and create a new
         * listening point for the ServiceUnit being started. The binding
         * information for the exact ServiceUnit to be started shall be
         * obtained via the Component-Provided binding information via
         * the SIP extensions.
         * CURRENTLY WE WILL JUST HARDCODE IT, AND WILL BE LOOKING AT THE
         * IMPLEMENTATION AT A LATER TIME.
         */
        try
        {
            startSipProvider(serviceUnitName);
            registerSipListener(serviceUnitName);
            activateEndpoints(serviceUnitName);
            startNmrListeners(serviceUnitName);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            throw new DeploymentException(ex.getMessage());
        }
        /**
         * Set the ServiceUnit Status to STARTED in the RuntimeComponentContext
         */
        setServiceUnitStatus(serviceUnitName, ServiceUnitStore.SERVICE_UNIT_STATUS_STARTED);
        logger.info(serviceUnitName + " has been started");
    }

    private void startNmrListeners(String serviceUnitName)
    {        
        logger.info("Starting Nmr Listeners for service unit: " + serviceUnitName);        
        /**
         * Get the required number of threads to be started from the main
         * component-level configuration file.
         */
        String noOfThreads = RuntimeComponentContext.getProperty("com.orange.uklab.b2bbc.NMR_LISTENERS_THREADS_COUNT");
        /**
         * Get the PooledExecutor and set its parameters
         */
        PooledExecutor pooledExecutor = new PooledExecutor();

        pooledExecutor.setMinimumPoolSize(Integer.parseInt(noOfThreads));
        pooledExecutor.setMaximumPoolSize(Integer.parseInt(noOfThreads) + 10);
        pooledExecutor.setKeepAliveTime(-1);
        
        logger.info("There are " + noOfThreads + " threads to be started...");
        /**
         * Use the obtained PooledExecutor to start new threads.
         */
        for(int i = 0 ; i < Integer.parseInt(noOfThreads) ; i++)
        {
            logger.fine("Starting thread " + i);
            try
            {
                pooledExecutor.execute(new NmrListenerImpl());
            }
            catch (InterruptedException ex)
            {
                logger.severe(ex.getMessage());
                ex.printStackTrace();
            }
            logger.fine("Thread " + i + " has been started...");
        }
        logger.info("Starting NMR Listeners completed...");
        /**
         * Add the started listeners to the RuntimeComponentContext
         */
        logger.info("Adding NMR PooledExecutor in the RuntimeComponentContext for ServiceUnit: " + serviceUnitName);
        RuntimeComponentContext.getInstance().addPooledExecutor(serviceUnitName, pooledExecutor);
    }

    private void stopNmrListeners(String serviceUnitName)
    {        
        logger.info("Stopping NMR Listeners for Service Unit: " + serviceUnitName);
        /**
         * Get an array of the registered NMR listeners with the given
         * Service Unit
         */
        PooledExecutor pooledExecutor = RuntimeComponentContext.getInstance().getPooledExecutor(serviceUnitName);
        /**
         * Interrupt all the worker threads currently running in the given
         * NMR PooledExecutor and then shutdown it.
         */
        logger.info("Interrupting the active working threads...");
        pooledExecutor.interruptAll();
        logger.info("Shutting down the PooledExecutor: " + serviceUnitName + " completed...");
        pooledExecutor.shutdownNow();
        /**
         * Remove the NmrListener from the RuntimeComponentContext
         */
        logger.info("Removing NMR PooledExecutor from the RuntimeComponentContext for ServiceUnit: " + serviceUnitName);
        RuntimeComponentContext.getInstance().removePooledExecutor(serviceUnitName);
    }

    private void activateEndpoints(String serviceUnitName) throws JBIException
    {
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        ServiceEndpoint[] serviceEndpoints = runtimeComponentContext.getServiceUnitEndpoints(serviceUnitName);
        logger.severe("Obtaining serviceEndpoints");
        if(serviceEndpoints != null)
        {
            logger.severe("LENGTH: " + serviceEndpoints.length);
            for(ServiceEndpoint serviceEndpoint : serviceEndpoints)
            {
                logger.severe("Interating Over the ServiceEndpoints");
                ServiceEndpointImpl serviceEndpointImpl = (ServiceEndpointImpl)serviceEndpoint;
                if(serviceEndpoint == null) logger.severe("serviceEndpoint shit, it is null");
                if(serviceEndpointImpl == null) logger.severe("serviceEndpointImpl shit, it is null");
                logger.severe("endpoint type:" + serviceEndpointImpl.getEndpointType());
                logger.severe("endpoint name:" + serviceEndpointImpl.getEndpointName());
                logger.severe("service unit name:" + serviceEndpointImpl.getServiceUnitName());
                
                if(serviceEndpointImpl.getEndpointType().equals(AbstractServiceEndpoint.ENDPOINT_TYPE_PROVIDE))
                {
                    logger.severe("Interacting with the NMR...");
                    NmrAgentImpl nmrAgentImpl = new NmrAgentImpl();
                    nmrAgentImpl.activateServiceEndpoint(serviceEndpoint);
                }
                else
                {
                    logger.info(this.getClass().getName() + ".activateEndpoints(): " + "Skipping CONSUME ServiceEndpoints found in this ServiceUnit: " + serviceUnitName + ": " + serviceEndpoint.getEndpointName());
                }
            }
        }
        else
        {
            logger.info(this.getClass().getName() + ".activateEndpoints(): " + "No ServiceEndpoints defined in this ServiceUnit: " + serviceUnitName);
        }
    }

    private void deActivateEndpoints(String serviceUnitName) throws JBIException
    {
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        ServiceEndpoint[] serviceEndpoints = runtimeComponentContext.getServiceUnitEndpoints(serviceUnitName);
        if(serviceEndpoints != null)
        {
            for(ServiceEndpoint serviceEndpoint : serviceEndpoints)
            {
                ServiceEndpointImpl serviceEndpointImpl = (ServiceEndpointImpl)serviceEndpoint;
                if(serviceEndpointImpl.getEndpointType().equals(AbstractServiceEndpoint.ENDPOINT_TYPE_PROVIDE))
                {
                    NmrAgentImpl nmrAgentImpl = new NmrAgentImpl();
                    nmrAgentImpl.deActivateServiceEndpoint(serviceEndpoint);
                }
                else
                {
                    logger.info(this.getClass().getName() + ".deActivateEndpoints(): " + "Skipping CONSUME ServiceEndpoints found in this ServiceUnit: " + serviceUnitName + ": " + serviceEndpoint.getEndpointName());
                }
            }
        }
        else
        {
            logger.info(this.getClass().getName() + ".deActivateEndpoints(): " + "No ServiceEndpoints defined in this ServiceUnit: " + serviceUnitName);
        }
    }
    
    /**
     * This method shall start a SipProvider associated with a specific
     * binding endpoint (ListeningPoint) obtained from the WSDL binding
     * information provided at deployment time. This method must also store
     * a refrence to the obtained SipProvider inside the RuntimeComponentContext
     */
    private void startSipProvider(String serviceUnitName) throws Exception
    {
        logger.info("Starting SIP Provider");
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        SipStack sipStack = runtimeComponentContext.getSipStack();
        ServiceUnitImpl serviceUnit = (ServiceUnitImpl)runtimeComponentContext.getServiceUnit(serviceUnitName);                
        ListeningPoint listeningPoint = sipStack.createListeningPoint(serviceUnit.getBindingAddress(), serviceUnit.getBindingPort(), serviceUnit.getBindingProtocol());
        SipProvider sipProvider = sipStack.createSipProvider(listeningPoint);
        runtimeComponentContext.addSipProvider(serviceUnitName, sipProvider);
    }

    private void registerSipListener(String serviceUnitName) throws Exception
    {
        if(serviceUnitName != null)
        {
            RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
            SipListener sipListener = ((SipStackImpl)(runtimeComponentContext.getSipStack())).getSipListener();
            SipProvider sipProvider = runtimeComponentContext.getSipProvider(serviceUnitName);
            logger.severe("################################################################################");
            if(((SipProviderImpl) sipProvider).getSipListener() == null)
                System.out.println("SipListener is null for " + serviceUnitName);
            logger.severe("################################################################################");
            if(sipListener == null)
            {                                                
                if(sipProvider != null)
                {                    
                    sipListener = new SipListenerImpl();
                    sipProvider.addSipListener(sipListener);
                    logger.info("SipListener has been registered by ServiceUnit: " + serviceUnitName);
                }
                else
                {
                    logger.severe("No SipProvider registered for ServiceUnit: " + serviceUnitName);
                    throw new Exception("No SipProvider registered for ServiceUnit: " + serviceUnitName);
                }
            }
            else
            {
                ((SipProviderImpl)sipProvider).setSipListener(sipListener);

                if(((SipProviderImpl) sipProvider).getSipListener() == null)
                    System.out.println("SipListener is null for " + serviceUnitName);
            }
        }
        else
        {
            logger.severe("The passed serviceUnitName is null");
            throw new Exception("The passed serviceUnitName is null");
        }
    }

    private void deRegisterSipListener(String serviceUnitName) throws Exception
    {
        if(serviceUnitName != null)
        {
            RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
            SipProvider sipProvider = runtimeComponentContext.getSipProvider(serviceUnitName);
            if(sipProvider != null)
            {                                
                SipListenerImpl sipListener = (SipListenerImpl)((SipStackImpl)runtimeComponentContext.getSipStack()).getSipListener();
                sipProvider.removeSipListener(sipListener);                
                logger.info("SipListener has been deregistered by ServiceUnit: " + serviceUnitName);
            }
            else
            {
                logger.severe("No SipProvider registered for ServiceUnit: " + serviceUnitName);
                throw new Exception("No SipProvider registered for ServiceUnit: " + serviceUnitName);
            }
        }
        else
        {
            logger.severe("The passed serviceUnitName is null");
            throw new Exception("The passed serviceUnitName is null");
        }
    }

    private void stopSipProvider(String serviceUnitName) throws Exception
    {
        logger.info("Stopping SIP Provider...");
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        SipStack sipStack = runtimeComponentContext.getSipStack();
        logger.info("Instance of the SipStack has been retrieved...");
        ServiceUnit serviceUnit = runtimeComponentContext.getServiceUnit(serviceUnitName);
        ListeningPoint listeningPoint = runtimeComponentContext.getSipProvider(serviceUnitName).getListeningPoint(serviceUnit.getBindingProtocol());        
        if(listeningPoint != null)
        {
            logger.info("Stopping listening point " + listeningPoint.getIPAddress());
            runtimeComponentContext.getSipProvider(serviceUnitName).removeListeningPoint(listeningPoint);
            sipStack.deleteListeningPoint(listeningPoint);
            sipStack.deleteSipProvider(runtimeComponentContext.getSipProvider(serviceUnitName));
            runtimeComponentContext.removeSipProvider(serviceUnitName);
        }
        else
        {
            logger.info("No Listening point found");
        }                
    }

    /**
     * This method is to set a respective ServiceUnit status and it is
     * used by the life cycle specific methods such as init, start, stop and
     * shutdown.
     * @param serviceUnitName
     * @param status
     */
    private void setServiceUnitStatus(String serviceUnitName, String status)
    {
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();
        try
        {
            runtimeComponentContext.setServiceUnitStatus(serviceUnitName, status);
        }
        catch (Exception ex)
        {
            logger.info(ex.getMessage());
            ex.printStackTrace();
        }
    }
    /**
     * 
     * @param arg0
     * @throws DeploymentException
     */
    @Override
    public void stop(String serviceUnitName) throws DeploymentException
    {
        logger.info("Stopping Service Unit named: " + serviceUnitName);
        /**
         * Set the ServiceUnit Status to STARTED in the RuntimeComponentContext
         */
        try
        {
            deRegisterSipListener(serviceUnitName);
            stopSipProvider(serviceUnitName);
            deActivateEndpoints(serviceUnitName);
            stopNmrListeners(serviceUnitName);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            throw new DeploymentException(ex.getMessage());
        }
        setServiceUnitStatus(serviceUnitName, ServiceUnitStore.SERVICE_UNIT_STATUS_STOPPED);
        logger.info(serviceUnitName + " has been stopped");
    }

    /**
     * 
     * @param arg0
     * @throws DeploymentException
     */
    @Override
    public void shutDown(String serviceUnitName) throws DeploymentException
    {
        logger.info("Shutting down Service Unit named: " + serviceUnitName);
        /**
         * Set the ServiceUnit Status to SHUTDOWN in the RuntimeComponentContext
         */
        setServiceUnitStatus(serviceUnitName, ServiceUnitStore.SERVICE_UNIT_STATUS_SHUTDOWN);
        logger.info(serviceUnitName + " has been shutdown");
    }

    /**
     * 
     * @param arg0
     * @param arg1
     * @return
     * @throws DeploymentException
     */
    @Override
    public String undeploy(String serviceUnitName, String serviceUnitRootPath) throws DeploymentException
    {
        logger.info("Undeploying Service Unit named: " + serviceUnitName);
        String resultMessage = undeployServiceUnit(serviceUnitName, serviceUnitRootPath);
        return resultMessage;
    }

    private String undeployServiceUnit(String serviceUnitName, String serviceUnitRootPath)
    {

        boolean unPersistentResult = false;
        boolean runtimeContextRemovalOutcome = false;
        RuntimeComponentContext runtimeComponentContext = RuntimeComponentContext.getInstance();;
        try
        {
            /**
             * Here we are unpersisting the service unit being undeployed from
             * the perminant disk persistence storage.
             */
            DataPersisterImpl dataPersisterImpl = new DataPersisterImpl();
            unPersistentResult = dataPersisterImpl.unpersistObject(serviceUnitName, "ServiceUnits");
            /**
             * Now we need to remove the ServiceUnit being undeployed from
             * the RuntimeComponentContext
             */            
            runtimeContextRemovalOutcome = runtimeComponentContext.removeServiceUnit(serviceUnitName);
        }
        catch(Exception ex)
        {
            logger.info(ex.getMessage());
            ex.printStackTrace();
        }
        /**
         * Depending on the outcome of the unpersistence and the removal of
         * the ServiceUnit being undeployed from the component, a ComponentTaskResult will be returned
         * back to the calling method representing either a SUCESS or FAILURE of the task.
         */
        TaskStatusMessage exceptionInfoTaskStatusMsg = new TaskStatusMessage(serviceUnitName + "UnDeploymentLocToken01", serviceUnitName + "MyLocMessage01", new String[] {serviceUnitName + "P1", serviceUnitName + "P2"});
        ExceptionInfo exceptionInfo = new ExceptionInfo(1, exceptionInfoTaskStatusMsg, "stack trace is not set");
        TaskStatusMessage taskStatusMessage = new TaskStatusMessage(serviceUnitName + "UnDeploymentLocToken02", serviceUnitName + "MyLocMessage02", new String[] {serviceUnitName + "P11", serviceUnitName + "P21"});
        ComponentTaskResultFactory componentTaskResultFactory = new ComponentTaskResultFactory(runtimeComponentContext.getComponentName());
        String resultMessage = null;
        String exitCode = ((unPersistentResult == true) && (runtimeContextRemovalOutcome == true)) ? ComponentTaskResultFactory.TASK_RESULT_SUCCESS : ComponentTaskResultFactory.TASK_RESULT_FAILED;
        try
        {
            resultMessage = componentTaskResultFactory.createMessage(serviceUnitName + "UnDeploymentTask", exitCode, ComponentTaskResultFactory.MESSAGE_TYPE_INFO, new TaskStatusMessage[]{taskStatusMessage}, new ExceptionInfo[]{exceptionInfo});
        }
        catch (B2BException ex)
        {
            Logger.getLogger(ServiceUnitManagerImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return resultMessage;
    }
}
