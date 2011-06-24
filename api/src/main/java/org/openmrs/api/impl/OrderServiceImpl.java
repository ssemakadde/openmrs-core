/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.GenericDrug;
import org.openmrs.ImplementationId;
import org.openmrs.Order;
import org.openmrs.Order.OrderAction;
import org.openmrs.OrderGroup;
import org.openmrs.OrderType;
import org.openmrs.Orderable;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.OrderDAO;
import org.openmrs.order.DrugOrderSupport;
import org.openmrs.order.RegimenSuggestion;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.validator.ValidateUtil;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the Order-related services class. This class should not be invoked by
 * itself. Spring injection is used to inject this implementation into the ServiceContext. Which
 * implementation is injected is determined by the spring application context file:
 * /metadata/api/spring/applicationContext.xml
 * 
 * @see org.openmrs.api.OrderService
 */
public class OrderServiceImpl extends BaseOpenmrsService implements OrderService {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	protected OrderDAO dao;
	
	public OrderServiceImpl() {
	}
	
	/**
	 * @see org.openmrs.api.OrderService#setOrderDAO(org.openmrs.api.db.OrderDAO)
	 */
	@Override
	public void setOrderDAO(OrderDAO dao) {
		this.dao = dao;
	}
	
	/**
	 * @see org.openmrs.api.OrderService#saveOrder(org.openmrs.Order)
	 */
	@Override
	public Order saveOrder(Order order) throws APIException {
		if (dao.isActivatedInDatabase(order))
			throw new APIException("Cannot modify an activated order");
		
		if (order.getOrderNumber() == null)
			order.setOrderNumber(getNewOrderNumber());
		
		ValidateUtil.validate(order);
		return dao.saveOrder(order);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#purgeOrder(org.openmrs.Order)
	 */
	@Override
	public void purgeOrder(Order order) throws APIException {
		purgeOrder(order, false);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#purgeOrder(Order)
	 */
	public void purgeOrder(Order order, boolean cascade) throws APIException {
		if (cascade) {
			// TODO delete other order stuff before deleting this order
			// (like DrugOrder?)
			throw new APIException("Cascade purging of Orders is not written yet");
		}
		
		dao.deleteOrder(order);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#voidOrder(org.openmrs.Order, java.lang.String)
	 */
	@Override
	public Order voidOrder(Order order, String voidReason) throws APIException {
		// fail early if this order is already voided
		if (order.getVoided())
			return order;
		
		if (!StringUtils.hasLength(voidReason))
			throw new IllegalArgumentException("voidReason cannot be empty or null");
		
		return dao.saveOrder(order);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#unvoidOrder(org.openmrs.Order)
	 */
	@Override
	public Order unvoidOrder(Order order) throws APIException {
		return dao.saveOrder(order);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#discontinueOrder(org.openmrs.Order, org.openmrs.Concept,
	 *      java.util.Date)
	 */
	@Override
	public Order discontinueOrder(Order order, Concept discontinueReason, Date discontinueDate) throws APIException {
		// TODO decide whether or discontinueDate can be in the future
		if (discontinueDate == null)
			discontinueDate = new Date();
		if (order.isDiscontinued(discontinueDate))
			throw new APIException("Order is already discontinued");
		
		// create a new Order that's a discontinuation order for the original one
		Order dc = new Order();
		dc.setOrderAction(OrderAction.DISCONTINUE);
		dc.setConcept(order.getConcept());
		Context.getOrderService().activateOrder(order, null, discontinueDate);
		
		// set the discontinuation properties on the original order
		order.setDiscontinued(true);
		order.setDiscontinuedDate(discontinueDate);
		order.setDiscontinuedReason(discontinueReason);
		order.setDiscontinuedBy(Context.getAuthenticatedUser());
		return dao.saveOrder(order);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#undiscontinueOrder(org.openmrs.Order)
	 */
	@Override
	public Order undiscontinueOrder(Order order) throws APIException {
		order.setDiscontinued(Boolean.FALSE);
		order.setDiscontinuedBy(null);
		order.setDiscontinuedDate(null);
		order.setDiscontinuedReason(null);
		//return Context.getOrderService().saveOrder(order);
		throw new APIException("TODO If we're going to allow this we need to also void the D/C order");
	}
	
	/**
	 * @see org.openmrs.api.OrderService#saveOrderType(org.openmrs.OrderType)
	 */
	@Override
	public OrderType saveOrderType(OrderType orderType) throws APIException {
		return dao.saveOrderType(orderType);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#retireOrderType(OrderType, String)
	 */
	@Override
	public OrderType retireOrderType(OrderType orderType, String reason) throws APIException {
		
		orderType.setRetired(true);
		orderType.setRetireReason(reason);
		
		return saveOrderType(orderType);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#unretireOrderType(org.openmrs.OrderType)
	 */
	@Override
	public OrderType unretireOrderType(OrderType orderType) throws APIException {
		orderType.setRetired(false);
		
		return saveOrderType(orderType);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#purgeOrderType(org.openmrs.OrderType)
	 */
	@Override
	public void purgeOrderType(OrderType orderType) throws APIException {
		dao.deleteOrderType(orderType);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrder(java.lang.Integer)
	 */
	@Override
	public Order getOrder(Integer orderId) throws APIException {
		return getOrder(orderId, Order.class);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrder(java.lang.Integer, java.lang.Class)
	 */
	@Override
	public <o extends Order> o getOrder(Integer orderId, Class<o> orderClassType) throws APIException {
		return dao.getOrder(orderId, orderClassType);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrders(java.lang.Class, java.util.List, java.util.List, org.openmrs.api.OrderService.ORDER_STATUS, java.util.List, java.util.List, java.util.List, java.util.Date)
	 */
	// TODO get rid of this method and anything that depends on it. Rewrite.
	public <Ord extends Order> List<Ord> getOrders(Class<Ord> orderClassType, List<Patient> patients,
	        List<Concept> concepts, ORDER_STATUS status, List<User> orderers, List<Encounter> encounters,
	        List<OrderType> orderTypes, Date asOfDate) {
		if (orderClassType == null)
			throw new APIException(
			        "orderClassType cannot be null.  An order type of Order.class or DrugOrder.class is required");
		
		if (patients == null)
			patients = new Vector<Patient>();
		
		if (concepts == null)
			concepts = new Vector<Concept>();
		
		if (status == null)
			status = ORDER_STATUS.ACTIVE;
		
		if (orderers == null)
			orderers = new Vector<User>();
		
		if (encounters == null)
			encounters = new Vector<Encounter>();
		
		if (orderTypes == null)
			orderTypes = new Vector<OrderType>();
		
		return dao.getOrders(orderClassType, patients, concepts, status, orderers, encounters, orderTypes, asOfDate);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrdersByPatient(org.openmrs.Patient)
	 */
	@Override
	public List<Order> getOrdersByPatient(Patient patient) throws APIException {
		if (patient == null)
			throw new APIException("Unable to get orders if I am not given a patient");
		
		List<Patient> patients = new Vector<Patient>();
		patients.add(patient);
		
		return getOrders(Order.class, patients, null, ORDER_STATUS.NOTVOIDED, null, null, null, null);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getDrugOrdersByPatient(org.openmrs.Patient,
	 *      org.openmrs.api.OrderService.ORDER_STATUS, boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<DrugOrder> getDrugOrdersByPatient(Patient patient, ORDER_STATUS orderStatus, boolean includeVoided) {
		if (patient == null)
			throw new APIException("Unable to get drug orders if not given a patient");
		
		List<Patient> patients = new Vector<Patient>();
		patients.add(patient);
		
		List<DrugOrder> drugOrders = getOrders(DrugOrder.class, patients, null, ORDER_STATUS.ANY, null, null, null, null);
		
		// loop over the drug orders and add them if they are within the current desired order
		if (drugOrders != null) {
			if (orderStatus == ORDER_STATUS.ANY)
				return drugOrders;
			else {
				// the user wants to limit the type of drug order to get, so loop over
				// them all and do the logic on each 
				
				List<DrugOrder> ret = new ArrayList<DrugOrder>();
				
				for (DrugOrder drugOrder : drugOrders) {
					if (orderStatus == ORDER_STATUS.ACTIVE && drugOrder.isCurrent())
						ret.add(drugOrder);
					else if (orderStatus == ORDER_STATUS.NOTVOIDED && !drugOrder.getVoided())
						ret.add(drugOrder);
					else if (orderStatus == ORDER_STATUS.COMPLETE && drugOrder.isDiscontinuedRightNow())
						ret.add(drugOrder);
				}
				
				return ret;
			}
		}
		
		// default return if no drug orders were found in the database
		return Collections.EMPTY_LIST;
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getDrugOrdersByPatient(org.openmrs.Patient,
	 *      org.openmrs.api.OrderService.ORDER_STATUS)
	 */
	@Override
	public List<DrugOrder> getDrugOrdersByPatient(Patient patient, ORDER_STATUS orderStatus) {
		return getDrugOrdersByPatient(patient, orderStatus, false);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getAllOrderTypes()
	 */
	@Override
	public List<OrderType> getAllOrderTypes() throws APIException {
		return getAllOrderTypes(true);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getAllOrderTypes(boolean)
	 */
	@Override
	public List<OrderType> getAllOrderTypes(boolean includeRetired) throws APIException {
		return dao.getAllOrderTypes(includeRetired);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderType(java.lang.Integer)
	 */
	@Override
	public OrderType getOrderType(Integer orderTypeId) throws APIException {
		return dao.getOrderType(orderTypeId);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getDrugOrdersByPatient(org.openmrs.Patient)
	 */
	@Override
	public List<DrugOrder> getDrugOrdersByPatient(Patient patient) throws APIException {
		List<Patient> patients = new Vector<Patient>();
		patients.add(patient);
		
		return getOrders(DrugOrder.class, patients, null, ORDER_STATUS.NOTVOIDED, null, null, null, null);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getStandardRegimens()
	 */
	@Override
	public List<RegimenSuggestion> getStandardRegimens() {
		DrugOrderSupport dos = null;
		List<RegimenSuggestion> standardRegimens = null;
		
		try {
			dos = DrugOrderSupport.getInstance();
		}
		catch (Exception e) {
			log.error("Error getting instance of DrugOrderSupport object", e);
		}
		
		if (dos != null) {
			standardRegimens = dos.getStandardRegimens();
		} else {
			log.error("DrugOrderSupport object is null after new instance");
		}
		
		return standardRegimens;
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderByUuid(java.lang.String)
	 */
	@Override
	public Order getOrderByUuid(String uuid) throws APIException {
		return dao.getOrderByUuid(uuid);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderTypeByUuid(java.lang.String)
	 */
	@Override
	public OrderType getOrderTypeByUuid(String uuid) throws APIException {
		return dao.getOrderTypeByUuid(uuid);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#saveActivatedOrder(org.openmrs.Order, org.openmrs.User, java.util.Date)
	 */
	@Override
	public Order signAndActivateOrder(Order order, User user, Date date) throws APIException {
		if (user == null)
			user = Context.getAuthenticatedUser();
		if (date == null)
			date = new Date();
		
		order = Context.getOrderService().signOrder(order, user, date);
		return Context.getOrderService().activateOrder(order, user, date);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#signOrder(org.openmrs.Order, org.openmrs.User, java.util.Date)
	 */
	@Override
	public Order signOrder(Order order, User provider, Date date) throws APIException {
		if (order.isSigned())
			throw new APIException("Order is already signed");
		
		if (provider == null)
			provider = Context.getAuthenticatedUser();
		if (date == null)
			date = new Date();
		order.setSignedBy(provider);
		order.setDateSigned(date);
		return Context.getOrderService().saveOrder(order); // TODO will probably fail if we try to sign an already-activated order
	}
	
	/**
	 * @see org.openmrs.api.OrderService#activateOrder(org.openmrs.Order, org.openmrs.User)
	 */
	@Override
	public Order activateOrder(Order order, User activatedBy, Date activationDate) throws APIException {
		if (activatedBy == null)
			activatedBy = Context.getAuthenticatedUser();
		if (activationDate == null)
			activationDate = new Date();
		order.setActivatedBy(activatedBy);
		order.setDateActivated(activationDate);
		return Context.getOrderService().saveOrder(order);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#fillOrder(org.openmrs.Order, org.openmrs.User, java.util.Date)
	 */
	@Override
	public Order fillOrder(Order order, User filler, Date dateFilled) throws APIException {
		return fillOrder(order, filler.getUserId() + filler.getSystemId(), dateFilled);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#fillOrder(org.openmrs.Order, java.lang.String, java.util.Date)
	 */
	@Override
	public Order fillOrder(Order order, String filler, Date dateFilled) throws APIException {
		if (!order.isSigned())
			throw new APIException("Can not fill an order which has not been signed");
		
		if (!order.isActivated())
			throw new APIException("Can not fill an order which has not been activated");
		
		if (dateFilled == null)
			dateFilled = new Date();
		else if (dateFilled.after(new Date()))
			throw new APIException("Cannot fill an order in the future");
		order.setDateFilled(dateFilled);
		order.setFiller(filler);
		return dao.saveOrder(order);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderByOrderNumber(java.lang.String)
	 */
	@Override
	public Order getOrderByOrderNumber(String orderNumber) {
		return dao.getOrderByOrderNumber(orderNumber);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderHistoryByConcept(org.openmrs.Patient, org.openmrs.Concept)
	 */
	@Override
	public List<Order> getOrderHistoryByConcept(Patient patient, Concept concept) {
		List<Concept> concepts = new Vector<Concept>();
		concepts.add(concept);
		
		List<Patient> patients = new Vector<Patient>();
		patients.add(patient);
		
		return getOrders(Order.class, patients, concepts, ORDER_STATUS.NOTVOIDED, null, null, null, null);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getNewOrderNumber()
	 */
	@Override
	public String getNewOrderNumber() {
		String orderNumber = Context.getAdministrationService().getGlobalProperty(
		    OpenmrsConstants.GP_ORDER_ENTRY_ORDER_NUMBER_PREFIX, "ORDER-")
		        + String.valueOf(dao.getMaximumOrderId() + 1);
		ImplementationId implementationId = Context.getAdministrationService().getImplementationId();
		if (implementationId != null && implementationId.getName() != null)
			orderNumber = implementationId.getName() + "-" + orderNumber;
		
		return orderNumber;
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getActiveOrdersByPatient(org.openmrs.Patient, java.util.Date)
	 */
	@Override
	public List<Order> getActiveOrdersByPatient(Patient p, Date date) throws APIException {
		
		if (p == null)
			throw new IllegalArgumentException("patient is required");
		
		if (date == null)
			date = new Date();
		
		List<Patient> patients = new Vector<Patient>();
		patients.add(p);
		
		return getOrders(Order.class, patients, null, ORDER_STATUS.ACTIVE, null, null, null, date);
		
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getActiveDrugOrdersByPatient(org.openmrs.Patient, java.util.Date)
	 */
	@Override
	public List<DrugOrder> getActiveDrugOrdersByPatient(Patient p, Date date) {
		if (p == null)
			throw new IllegalArgumentException("patient is required");
		
		if (date == null)
			date = new Date();
		
		List<Patient> patients = new Vector<Patient>();
		patients.add(p);
		
		return getOrders(DrugOrder.class, patients, null, ORDER_STATUS.ACTIVE, null, null, null, date);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderables(java.lang.String)
	 */
	@Override
	public List<Orderable<?>> getOrderables(String query) throws APIException {
		
		if (query == null)
			throw new IllegalArgumentException("Orderable concept name is required");
		
		List<Orderable<?>> result = new ArrayList<Orderable<?>>();
		List<Concept> concepts = Context.getConceptService().getConceptsByName(query);
		if (concepts != null) {
			for (Concept concept : concepts) {
				if (concept.getConceptClass().getName().equals("Drug"))
					result.add(new GenericDrug(concept));
			}
		}
		
		// and next to try to find drugs by name
		List<Drug> drugs = Context.getConceptService().getDrugs(query);
		if (drugs != null) {
			for (Drug drug : drugs) {
				if (!drug.isRetired())
					result.add(drug);
			}
		}
		
		return result;
	}
	
	@Override
	public OrderGroup signAndActivateOrderGroup(OrderGroup group, User user, Date activated) throws APIException {
		if (group.getOrderGroupId() != null)
			throw new APIException(
			        "signAndActivateOrderGroup Can not be called for an existing orders group. Please use a new orders group.");
		
		if (group.getMembers().isEmpty())
			throw new APIException("signAndActivateOrderGroup Can not be called for an orders group with no orders therein.");
		
		for (Order order : group.getMembers())
			Context.getOrderService().signAndActivateOrder(order, user, activated);
		
		group = Context.getOrderService().saveOrderGroup(group);
		
		return group;
	}
	
	/**
	 * Convenient method that can be used to sign and activate orders within groups
	 * 
	 * @param order the order to be activated
	 * @param user the user who activates order
	 * @param activated the date of activation
	 */
	private void signAndActivateOrder2(Order order, User user, Date activated) throws APIException {
		
		if (order.getOrderId() != null)
			throw new APIException("saveActivatedOrder Can not be called for an existing order. Please use a new order.");
		
		order.setOrderAction(OrderAction.NEW);
		order = Context.getOrderService().signOrder(order, user, activated);
		Context.getOrderService().activateOrder(order, user, activated);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#saveOrderGroup(org.openmrs.OrderGroup)
	 */
	@Override
	public OrderGroup saveOrderGroup(OrderGroup group) throws APIException {
		return dao.saveOrderGroup(group);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#voidOrderGroup(org.openmrs.OrderGroup)
	 */
	@Override
	public OrderGroup voidOrderGroup(OrderGroup group, String voidReason) throws APIException {
		// fail early if this order group is already voided
		if (group.getVoided())
			return group;
		
		if (!StringUtils.hasLength(voidReason))
			throw new IllegalArgumentException("voidReason cannot be empty or null");
		
		group.setVoided(Boolean.TRUE);
		group.setVoidReason(voidReason);
		group.setVoidedBy(Context.getAuthenticatedUser());
		if (group.getDateVoided() == null)
			group.setDateVoided(new Date());
		
		return dao.saveOrderGroup(group);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#unvoidOrderGroup(org.openmrs.OrderGroup)
	 */
	@Override
	public OrderGroup unvoidOrderGroup(OrderGroup group) throws APIException {
		group.setVoided(false);
		group.setVoidedBy(null);
		group.setVoidReason(null);
		group.setDateVoided(null);
		
		return dao.saveOrderGroup(group);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderGroup(java.lang.Integer)
	 */
	@Override
	public OrderGroup getOrderGroup(Integer orderGroupId) throws APIException {
		return dao.getOrderGroup(orderGroupId);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderGroupByUuid(java.lang.String)
	 */
	@Override
	public OrderGroup getOrderGroupByUuid(String uuid) throws APIException {
		return dao.getOrderGroupByUuid(uuid);
	}
	
	/**
	 * @see org.openmrs.api.OrderService#getOrderGroupsByPatient(org.openmrs.Patient)
	 */
	@Override
	public List<OrderGroup> getOrderGroupsByPatient(Patient patient) throws APIException {
		if (patient == null)
			throw new IllegalArgumentException("patient is required");
		return dao.getOrderGroupsByPatient(patient);
	}
	
}
