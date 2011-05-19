/*
 * Copyright 2011 The Kuali Foundation Licensed under the Educational Community
 * License, Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.opensource.org/licenses/ecl1.php Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.kuali.rice.kns.uif.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.kuali.rice.kns.UserSession;
import org.kuali.rice.kns.service.KNSServiceLocatorWeb;
import org.kuali.rice.kns.service.SessionDocumentService;
import org.kuali.rice.kns.uif.UifConstants;
import org.kuali.rice.kns.uif.UifParameters;
import org.kuali.rice.kns.uif.container.View;
import org.kuali.rice.kns.uif.history.History;
import org.kuali.rice.kns.uif.service.ViewService;
import org.kuali.rice.kns.util.KNSConstants;
import org.kuali.rice.kns.web.spring.controller.UifControllerBase;
import org.kuali.rice.kns.web.spring.form.DocumentFormBase;
import org.kuali.rice.kns.web.spring.form.UifFormBase;
import org.springframework.web.servlet.ModelAndView;

/**
 * This class provides helper methods that will be used during the request
 * lifecycle.
 * <p>
 * This class was created to avoid duplication of the methods used by 
 * the UifHandlerExceptionResolver
 * </p>
 * 
 * @author Kuali Rice Team (rice.collab@kuali.org)
 */
public class UifWebUtils {

    private static final Logger LOG = Logger.getLogger(UifWebUtils.class);

    /**
     * Gets the form from the request.
     * <p>
     * Looks for the form on the session by using the form key. If the form is not
     * on the session it will attempt to get it from the database.
     * </p>
     * 
     * @param request
     *            the http request
     * @return the form from request
     */
    public static UifFormBase getFormFromRequest(HttpServletRequest request) {
        UifFormBase form = null;
        String formKeyParam = request.getParameter(UifParameters.FORM_KEY);
        String docId = request.getParameter(KNSConstants.DOCUMENT_DOCUMENT_NUMBER);
        if (StringUtils.isNotBlank(formKeyParam)) {
            form = (UifFormBase) request.getSession().getAttribute(formKeyParam);
            // retreive from db if form not in session
            if (form == null) {
                UserSession userSession = (UserSession) request.getSession()
                        .getAttribute(KNSConstants.USER_SESSION_KEY);
                form = KNSServiceLocatorWeb.getSessionDocumentService().getUifDocumentForm(docId, formKeyParam,
                        userSession, request.getRemoteAddr());
            }
        }
        return form;
    }

    /**
     * Configures the <code>ModelAndView</code> instance containing the form
     * data and pointing to the UIF generic spring view
     * 
     * @param form
     *            - Form instance containing the model data
     * @param viewId
     *            - Id of the View to return
     * @param pageId
     *            - Id of the page within the view that should be rendered, can
     *            be left blank in which the current or default page is rendered
     * @return ModelAndView object with the contained form
     */
    public static ModelAndView getUIFModelAndView(UifFormBase form, String viewId, String pageId) {
        // update form with the requested view id and page
        form.setViewId(viewId);
        if (StringUtils.isNotBlank(pageId)) {
            form.setPageId(pageId);
        }

        // create the spring return object pointing to View.jsp
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject(UifConstants.DEFAULT_MODEL_NAME, form);
        modelAndView.setViewName(UifConstants.SPRING_VIEW_ID);

        return modelAndView;
    }

    /**
     * After the controller logic is executed, the form is placed into session
     * and the corresponding view is prepared for rendering
     */
    public static void postControllerHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) throws Exception {
        if (handler instanceof UifControllerBase) {
            UifControllerBase controller = (UifControllerBase) handler;
            UifFormBase form = null;

            Object model = modelAndView.getModelMap().get(UifConstants.DEFAULT_MODEL_NAME);
            if (model instanceof UifFormBase) {
                form = (UifFormBase) model;
                
                
                View view = form.getView();
                
                //Determining the property name to use as part of the breadcrumb/title
                view.determineViewLabelPropertyName();
                
                //Main history/breadcrumb tracking support
                History history = form.getFormHistory();
                if(history == null){
                    history = new History();
                    history.setHomewardPath(view.getBreadcrumbs().getHomewardPathList());
                    history.setAppendHomewardPath(view.getBreadcrumbs().isDisplayHomewardPath());
                    history.setAppendPassedHistory(view.getBreadcrumbs().isDisplayPassedHistory());
                    
                    //Passed settings ALWAYS override the defaults
                    if(StringUtils.isNotBlank(request.getParameter(UifConstants.UrlParams.SHOW_HOME))){
                        history.setAppendHomewardPath(Boolean.parseBoolean(request.getParameter(UifConstants.UrlParams.SHOW_HOME)));
                    }
                    if(StringUtils.isNotBlank(request.getParameter(UifConstants.UrlParams.SHOW_HISTORY))){
                        history.setAppendPassedHistory(Boolean.parseBoolean(request.getParameter(UifConstants.UrlParams.SHOW_HISTORY)));
                    }
                    history.setCurrent(form, request);
                    history.buildHistoryFromParameterString(request.getParameter(UifConstants.UrlParams.HISTORY));
                    form.setFormHistory(history);
                }
            }

            form.setPreviousView(null);

            //Store form to session and persist document form to db as well
            request.getSession().setAttribute(form.getFormKey(), model);
            if (form instanceof DocumentFormBase){
                UserSession userSession = (UserSession) request.getSession().getAttribute(KNSConstants.USER_SESSION_KEY);            
                getSessionDocumentService().setDocumentForm((DocumentFormBase)form, userSession, request.getRemoteAddr());
            }

            // perform authorization of controller method
            checkMethodToCallAuthorization(request, controller, form);

            // prepare view contained in form
            prepareViewForRendering(form);
        }
    }

    /**
     * Verify the user is authorized to invoke the controller method according
     * to the module that owns the functionality. This is done post handle to be
     * able to access the form and whatever processing was done TODO: should
     * this be throwing some exception?
     * 
     * @param request
     *            - current HTTP request containing method to call parameter
     * @param controller
     *            - controller that was invoked
     * @param form
     *            - form instance containing the data
     */
    public static void checkMethodToCallAuthorization(HttpServletRequest request, UifControllerBase controller,
            UifFormBase form) {
        // currently methodToCall must be a regularly parseable request
        // parameter, so just get from request
        String methodToCall = request.getParameter(KNSConstants.DISPATCH_REQUEST_PARAMETER);

        if (!controller.getMethodToCallsToNotCheckAuthorization().contains(methodToCall)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("'" + methodToCall + "' not in set of excempt methods: "
                        + controller.getMethodToCallsToNotCheckAuthorization());
            }

            controller.checkAuthorization(form, methodToCall);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("'" + methodToCall + "' is exempt from auth checks.");
            }
        }
    }

    /**
     * Prepares the <code>View</code> instance contained on the form for
     * rendering
     * <p>
     * First a check is made to verify the view instance contained on the form
     * has the same id as the view id on the form (id that was requested), if
     * not a new view instance is retrieved for that view id. Then a check on
     * the view status is made to determine if we need to run the full view
     * life-cycle (in the case of a finalized view), or just the build steps
     * (apply model and finalize). Finally the page is set on the view to
     * reflect the page that was requested
     * </p>
     * 
     * @param form
     *            - form instance containing the data and view instance
     */
    public static void prepareViewForRendering(UifFormBase form) {
        // if we don't have the view instance or a different view was
        // requested get new instance from the view service
        View view = form.getView();
        String viewId = form.getViewId();
        if ((view == null) || !StringUtils.equals(viewId, view.getId())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting new view instance for view id: " + viewId);
            }

            view = getViewService().getView(viewId, form.getViewRequestParameters());

            // view changed so force full render
            form.setRenderFullView(true);
        }

        // if view status is final we need to rebuild (build fresh)
        if (StringUtils.equals(UifConstants.ViewStatus.FINAL, view.getViewStatus())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rebuilding view due to final status, view id: " + viewId);
            }

            view = getViewService().rebuildView(viewId, form, form.getViewRequestParameters());
        } else {
            // update the view with the model data
            getViewService().buildView(view, form);
        }

        // set dirty flag
        form.setValidateDirty(view.isValidateDirty());

        // set view page to page requested on form
        if (StringUtils.isNotBlank(form.getPageId())) {
            view.setCurrentPageId(form.getPageId());
        }
    }

    protected static SessionDocumentService getSessionDocumentService() {
        return KNSServiceLocatorWeb.getSessionDocumentService();
    }

    protected static ViewService getViewService() {
        return KNSServiceLocatorWeb.getViewService();
    }

}
