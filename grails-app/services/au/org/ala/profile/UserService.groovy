package au.org.ala.profile

import au.org.ala.web.UserDetails
import grails.transaction.Transactional

@Transactional
class UserService {

    static transactional = false
    def authService

    private static ThreadLocal<UserDetails> _currentUser = new ThreadLocal<UserDetails>()

    def getCurrentUserDisplayName() {
        UserDetails currentUser = _currentUser.get()
        currentUser ? currentUser.displayName : ""
    }

    def getCurrentUserDetails() {
        _currentUser.get();
    }

    /**
     * This method gets called by a filter at the beginning of the request (if a userId paramter is on the URL)
     * It sets the user details in a thread local for extraction by the audit service.
     * @param userId
     */
    def setCurrentUser(String userId) {

        def userDetails = authService.getUserForUserId(userId)
        if (userDetails) {
            _currentUser.set(userDetails)
        } else {
            log.warn("Failed to lookup user details for user id ${userId}! No details set on thread local.")
        }
        userDetails
    }

    def clearCurrentUser() {
        if (_currentUser) {
            _currentUser.remove()
        }
    }
}