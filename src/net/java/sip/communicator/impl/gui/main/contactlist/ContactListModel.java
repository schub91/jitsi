/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.gui.main.contactlist;

import java.util.*;

import javax.swing.*;

import net.java.sip.communicator.impl.gui.utils.*;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.protocol.*;

/**
 * The list model of the ContactList. This class use as a data model the
 * <tt>MetaContactListService</tt> itself. The <tt>ContactListModel</tt>
 * plays only a role of a "list" face of the "tree" MetaContactListService
 * structure. It provides an implementation of the AbstractListModel and adds
 * some methods facilitating the access to the contact list. Some more contact
 * list specific methods are added like: getMetaContactStatus,
 * getMetaContactStatusIcon, changeContactStatus, etc.
 * 
 * @author Yana Stamcheva
 *
 */
public class ContactListModel extends AbstractListModel {

    private MetaContactListService contactList;

    private MetaContactGroup rootGroup;

    private Vector closedGroups = new Vector();

    private boolean showOffline = true;

    /**
     * Creates a List Model, which gets its data from 
     * the given MetaContactListService. 
     * 
     * @param contactList The MetaContactListService 
     * which contains the contact list.
     */
    public ContactListModel(MetaContactListService contactList) {

        this.contactList = contactList;

        this.rootGroup = this.contactList.getRoot();
    }

    /**
     * Informs interested listeners that the content has 
     * changed of the cells given by the range from 
     * startIndex to endIndex.
     * 
     * @param startIndex The start index of the range .
     * @param endIndex The end index of the range.
     */
    public void contentChanged(int startIndex, int endIndex) {
        fireContentsChanged(this, startIndex, endIndex);
    }

    /**
     * Informs interested listeners that new cells are 
     * added from startIndex to endIndex. 
     * 
     * @param startIndex The start index of the range .
     * @param endIndex The end index of the range.
     */
    public void contentAdded(final int startIndex, final int endIndex) {
        SwingUtilities.invokeLater(new Thread() {
            public void run() {
                fireIntervalAdded(this, startIndex, endIndex);
            }
        });
    }

    /**
     * Informs interested listeners that a range of cells is removed.
     * 
     * @param startIndex The start index of the range.
     * @param endIndex The end index of the range.
     */
    public void contentRemoved(final int startIndex, final int endIndex) {
        SwingUtilities.invokeLater(new Thread() {
            public void run() {
                fireIntervalRemoved(this, startIndex, endIndex);
            }
        });
    }

    /**
     * Returns the size of this list model.
     * @return The size of this list model.
     */
    public int getSize() {
        return this.getContactListSize(rootGroup);
    }

    /**
     * Returns the object at the given index.
     * @param index The index.
     * @return The object at the given index.
     */
    public Object getElementAt(int index) {
        Object element = this.getElementAt(this.rootGroup, -1, index);
        return element;
    }

    /**
     * Goes through all subgroups and contacts and determines 
     * the final size of the contact list.
     * 
     * @param group The group which to be measured.
     * @return The size of the contactlist
     */
    private int getContactListSize(MetaContactGroup group) {
        int size = 0;

        if (!this.isGroupClosed(group)) {
            if (showOffline) {
                size = group.countChildContacts();
            }
            else {
                Iterator i = group.getChildContacts();
                while (i.hasNext()) {
                    MetaContact contact = (MetaContact) i.next();
                    
                    if (isContactOnline(contact))
                        size++;
                }
            }
            size += group.countSubgroups();

            Iterator subgroups = group.getSubgroups();

            while (subgroups.hasNext()) {
                size += getContactListSize((MetaContactGroup) subgroups.next());
            }
        }
        return size;
    }

    /**
     * Returns the general status of the given MetaContact. Detects the 
     * status using the priority status table. The priority is defined 
     * on the "availablity" factor and here the most "available" status 
     * is returned.
     * 
     * @param metaContact The metaContact fot which the status is asked.
     * @return PresenceStatus The most "available" status 
     * from all subcontact  statuses.
     */
    public PresenceStatus getMetaContactStatus(MetaContact metaContact) {
        PresenceStatus status = null;
        Iterator i = metaContact.getContacts();
        while (i.hasNext()) {
            Contact protoContact = (Contact) i.next();
            PresenceStatus contactStatus = protoContact.getPresenceStatus();

            if(status == null) {
                status = contactStatus;
            }
            else {
                status
                    = (contactStatus.compareTo(status) > 0)
                    ? contactStatus : status;
            }
        }
        return status;
    }

    /**
     * Returns the status icon for this MetaContact.
     * 
     * @param contact The metaContact for which the status icon is asked.
     * @return the status icon for this MetaContact.
     */
    public ImageIcon getMetaContactStatusIcon(MetaContact contact) {
        return new ImageIcon(Constants.getStatusIcon(this
                .getMetaContactStatus(contact)));
    }

    /**
     * Returns the index of the given MetaContact.
     *  
     * @param contact The MetaContact to search for.
     * @return The index of the given MetaContact.
     */
    public int indexOf(MetaContact contact) {

        int index = -1;

        if (showOffline || isContactOnline(contact)) {
            int currentIndex = 0;
            MetaContactGroup parentGroup = this.contactList
                    .findParentMetaContactGroup(contact);

            if (parentGroup != null && !this.isGroupClosed(parentGroup)) {

                currentIndex += this.indexOf(parentGroup);

                currentIndex += parentGroup.indexOf(contact) + 1;

                index = currentIndex;
            }
        }
        return index;
    }

    /**
     * Returns the index of the given MetaContactGroup.
     * 
     * @param group The given MetaContactGroup to search for.
     * @return The index of the given MetaContactGroup.
     */
    public int indexOf(MetaContactGroup group) {

        int index = -1;
        int currentIndex = 0;
        MetaContactGroup parentGroup = this.contactList
                .findParentMetaContactGroup(group);

        if (parentGroup != null && !this.isGroupClosed(parentGroup)) {
          
            currentIndex += this.indexOf(parentGroup);
          
            currentIndex += countDirectChildContacts(parentGroup);
          
            currentIndex += parentGroup.indexOf(group) + 1;
            
            for (int i = 0; i < parentGroup.indexOf(group); i++) {
                MetaContactGroup subGroup = parentGroup
                        .getMetaContactSubgroup(i);
                
                currentIndex += countContactsAndSubgroups(subGroup);
            }            
            index = currentIndex;
        }
        return index;
    }

    /**
     * Returns the number of all children of the given
     * MetaContactGroup. Counts in depth all subgroups 
     * and child contacts.
     * 
     * @param parentGroup The parent MetaContactGroup.
     * @return The number of all children of the given MetaContactGroup
     */
    public int countContactsAndSubgroups(MetaContactGroup parentGroup) {
 
        int count = 0;

        if (parentGroup != null && !this.isGroupClosed(parentGroup)) {
            if (showOffline) {
                count = parentGroup.countChildContacts();
            }
            else {                
                Iterator i = parentGroup.getChildContacts();
                while (i.hasNext()) {
                    MetaContact contact = (MetaContact) i.next();
                    if (isContactOnline(contact))
                        count++;
                }
            }

            Iterator subgroups = parentGroup.getSubgroups();

            while (subgroups.hasNext()) {
                MetaContactGroup subgroup = (MetaContactGroup) subgroups.next();

                count += countContactsAndSubgroups(subgroup);
            }
        }
        return count;
    }
    
    /**
     * Returns the number of all child contacts of the given
     * MetaContactGroup.
     * 
     * @param parentGroup The parent MetaContactGroup.
     * @return The number of all children of the given MetaContactGroup
     */
    public int countDirectChildContacts(MetaContactGroup parentGroup) {
 
        int count = 0;

        if (parentGroup != null && !this.isGroupClosed(parentGroup)) {
            if (showOffline) {
                count = parentGroup.countChildContacts();
            }
            else {                
                Iterator i = parentGroup.getChildContacts();
                while (i.hasNext()) {
                    MetaContact contact = (MetaContact) i.next();
                    if (isContactOnline(contact))
                        count++;
                }
            }
        }
        return count;
    }

    /**
     * Recursively searches all groups for the element at the given index. 
     * 
     * @param group The group in which we search.
     * @param searchedIndex The index to search for.
     * @return The element at the given index, if it finds it, otherwise null.
     */
    private Object getElementAt(MetaContactGroup group,
                int currentIndex, int searchedIndex) {
        
        Object element = null;
        if(currentIndex == searchedIndex) {
            element = group;
        }
        else {
            if(!isGroupClosed(group)) {
                int childCount = countChildContacts(group);
                if(searchedIndex <= (currentIndex + childCount)) {
                    
                    MetaContact contact = group.getMetaContact(
                            searchedIndex - currentIndex - 1);
                    
                    if(showOffline || isContactOnline(contact))
                        element = contact;
                }
                else {
                    currentIndex += childCount;
                    Iterator subgroups = group.getSubgroups();
    
                    while (subgroups.hasNext()) {
                        MetaContactGroup subgroup
                            = (MetaContactGroup) subgroups.next();
                        element = getElementAt(
                                subgroup, currentIndex + 1, searchedIndex);
                        
                        if(element != null)
                            break;
                        else {
                            if(!isGroupClosed(subgroup))
                                currentIndex
                                    += countChildContacts(subgroup) + 1;
                            else
                                currentIndex ++;
                        }
                    }
                }
            }
        }
        return element;
    }

    /**
     * Closes the given group by hiding all containing contacts.
     * 
     * @param group The group to close.
     */
    public void closeGroup(MetaContactGroup group) {        
        if (countContactsAndSubgroups(group) > 0) {
            contentRemoved(this.indexOf(group.getMetaContact(0)),
                this.indexOf(group.getMetaContact(
                        countContactsAndSubgroups(group) - 1)));
            
            this.closedGroups.add(group);
        }
    }

    /**
     * Opens the given group by showing all containing contacts.
     * 
     * @param group The group to open.
     */
    public void openGroup(MetaContactGroup group) {        
        this.closedGroups.remove(group);
        contentAdded(this.indexOf(group.getMetaContact(0)), 
            this.indexOf(group.getMetaContact(
                    countContactsAndSubgroups(group) - 1)));
    }

    /**
     * Checks whether the group is closed.
     * 
     * @param group The group to check.
     * @return True if the group is closed, false - otherwise.
     */
    public boolean isGroupClosed(MetaContactGroup group) {
        if (this.closedGroups.contains(group))
            return true;
        else
            return false;
    }

    /**
     * Returns true if offline contacts should be shown, 
     * false otherwise.
     * @return boolean true if offline contacts should be 
     * shown, false otherwise.
     */
    public boolean showOffline() {
        return showOffline;
    }

    /**
     * Sets the showOffline variable to indicate whether or not 
     * offline contacts should be shown.
     * @param showOffline true if offline contacts should be shown, 
     * false otherwise.
     */
    public void setShowOffline(boolean showOffline) {
        this.showOffline = showOffline;
        this.contentChanged(0, getSize() - 1);
    }

    /**
     * Informs the model that the contect of the given contact 
     * was changed. When in mode "hide offline contacts", shows
     * or hides the given contact depending on the new status.
     *  
     * @param contact The MetaContact which status has changed.
     * @param newStatus The new status of the contact.
     */
    public void updateContactStatus(MetaContact contact,
            PresenceStatus newStatus) {        
        int index = this.indexOf(contact);
        this.contentChanged(index, index);
    }
    
    /**
     * Returns TRUE if the given meta contact is online, FALSE otherwise.
     * @param contact the meta contact
     * @return TRUE if the given meta contact is online, FALSE otherwise
     */
    public boolean isContactOnline(MetaContact contact)
    {
        //Lays on the fact that the default contact is the most connected.        
        if(contact.getDefaultContact().getPresenceStatus().getStatus()
                > PresenceStatus.ONLINE_THRESHOLD) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public int countChildContacts(MetaContactGroup group)
    { 
        if(showOffline)
            return group.countChildContacts();
        else {
            int count = 0;
            Iterator i = group.getChildContacts();
            while(i.hasNext()) {
                MetaContact metaContact = (MetaContact)i.next();
                if(isContactOnline(metaContact)) {
                    count ++;
                }
                else {
                    break;
                }
            }
            return count;
        }
    }
}
