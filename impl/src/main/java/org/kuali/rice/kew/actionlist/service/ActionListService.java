/*
 * Copyright 2005-2006 The Kuali Foundation.
 *
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kuali.rice.kew.actionlist.service;

import java.util.Collection;
import java.util.List;

import org.kuali.rice.kew.actionitem.ActionItem;
import org.kuali.rice.kew.actionitem.OutboxItemActionListExtension;
import org.kuali.rice.kew.actionlist.ActionListFilter;
import org.kuali.rice.kew.actionrequest.ActionRequestValue;
import org.kuali.rice.kew.actionrequest.Recipient;



/**
 * Main service for doing action list data access work
 *
 * @author Kuali Rice Team (kuali-rice@googlegroups.com)
 *
 */
public interface ActionListService {
    public ActionItem createActionItemForActionRequest(ActionRequestValue actionRequest);

    public Collection<ActionItem> getActionList(String principalId, ActionListFilter filter);

    public Collection<ActionItem> getActionListForSingleDocument(Long routeHeaderId);

    public Collection<Recipient> findUserSecondaryDelegators(String principalId);

    public Collection<Recipient> findUserPrimaryDelegations(String principalId);

    public boolean refreshActionList(String principalId);

    public void saveActionItem(ActionItem actionItem);

    public void deleteActionItem(ActionItem actionItem);
    
    public void deleteActionItem(ActionItem actionItem, boolean forceIntoOutbox);

    public void deleteByRouteHeaderId(Long routeHeaderId);

    public Collection<ActionItem> findByPrincipalId(String principalId);

    public Collection<ActionItem> findByWorkflowUserRouteHeaderId(String workflowUserId, Long routeHeaderId);

    public Collection<ActionItem> findByRouteHeaderId(Long routeHeaderId);

    public Collection<ActionItem> findByDocumentTypeName(String documentTypeName);

    public void updateActionItemsForTitleChange(Long routeHeaderId, String newTitle);

    public void validateActionItem(ActionItem actionItem);

    public ActionItem findByActionItemId(Long actionItemId);

    /**
     * Retrieves the number of Action List items in the given user's primary Action List (does not include secondary delegations)
     */
    public int getCount(String principalId);

    public void saveRefreshUserOption(String principalId);


    /**
     *
     * Retrieves {@link OutboxItemActionListExtension} items for the given user
     *
     * @param principalId
     * @param filter
     * @return
     */
    public Collection<ActionItem> getOutbox(String principalId, ActionListFilter filter);
    public Collection<ActionItem> getOutboxItemsByDocumentType(String documentTypeName);
    public void removeOutboxItems(String principalId, List<Long> outboxItems);
    public void saveOutboxItem(ActionItem actionItem);
    public void saveOutboxItem(ActionItem actionItem, boolean forceIntoOutbox);
}
