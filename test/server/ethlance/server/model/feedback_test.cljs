(ns ethlance.server.model.feedback-test
  "Unit tests for the feedback models."
  (:require
   [clojure.test :refer [deftest is are testing]]
   [bignumber.core :as bn]
   [cuerdas.core :as str]
   [district.server.config]
   [district.server.db :as district.db]
   [taoensso.timbre :as log]
   [ethlance.server.db :as ethlance.db]
   [ethlance.server.model.user :as user]
   [ethlance.server.model.arbiter :as arbiter]
   [ethlance.server.model.candidate :as candidate]
   [ethlance.server.model.employer :as employer]
   [ethlance.server.model.job :as job]
   [ethlance.server.model.feedback :as feedback]
   [ethlance.server.test-utils.db :refer [deftest-database] :include-macros true]
   [ethlance.shared.enumeration.currency-type :as enum.currency]
   [ethlance.shared.enumeration.bid-option :as enum.bid-option]
   [ethlance.shared.enumeration.boolean :as enum.boolean]
   [ethlance.shared.enumeration.contract-status :as enum.status]
   [ethlance.shared.enumeration.payment-type :as enum.payment]
   [ethlance.shared.enumeration.availability :as enum.availability]
   [ethlance.shared.enumeration.user-type :as enum.user-type]))


(deftest-database main-feedback-model {}
  ;;
  ;; Setup
  ;;

  ;; Employer
  (user/register! {:user/id 1
                   :user/address "0x1"
                   :user/country-code "CA"
                   :user/email "john.doe@gmail.com"
                   :user/profile-image ""
                   :user/date-updated 1
                   :user/date-created 1})

  (employer/register! {:user/id 1
                       :employer/biography "A testy fellow"
                       :employer/date-registered 0
                       :employer/professional-title "Project Manager"})

  ;; Candidate
  (user/register! {:user/id 2
                   :user/address "0x2"
                   :user/country-code "US"
                   :user/email "jane.doe@gmail.com"
                   :user/profile-image ""
                   :user/date-updated 1
                   :user/date-created 1})

  (candidate/register! {:user/id 2
                        :candidate/biography "A testy fellow"
                        :candidate/date-registered 0
                        :candidate/professional-title "Software Developer"})

  ;; Arbiter
  (user/register! {:user/id 3
                   :user/address "0x3"
                   :user/country-code "RU"
                   :user/email "nicholai.pavlov@gmail.com"
                   :user/profile-image ""
                   :user/date-updated 3
                   :user/date-created 3})

  (arbiter/register! {:user/id 3
                      :arbiter/biography "I am testy."
                      :arbiter/date-registered 3
                      :arbiter/currency-type ::enum.currency/eth
                      :arbiter/payment-value 5
                      :arbiter/payment-type ::enum.payment/percentage})

  (testing "Creating a job"
    (job/create-job! {:job/index 0
                      :job/title "Full-Stack Java Developer"
                      :job/availability ::enum.availability/full-time
                      :job/bid-option ::enum.bid-option/hourly-rate
                      :job/category "Software Development"
                      :job/description "The job is great."
                      :job/date-created 5
                      :job/employer-uid "0x1"
                      :job/estimated-length-seconds (* 30 24 60 60)
                      :job/include-ether-token? true
                      :job/is-invitation-only? false}))

  (testing "Adding arbiter requests"
    (is (= (count (job/arbiter-request-listing 0)) 0))

    (job/add-arbiter-request! {:job/index 0
                               :user/id 3
                               :arbiter-request/date-requested 6
                               :arbiter-request/is-employer-request? true})

    (is (thrown? js/Error (job/add-arbiter-request! {:job/index 0
                                                     :user/id 3
                                                     :arbiter-request/date-requested 6
                                                     :arbiter-request/is-employer-request? true})))

    (is (= (count (job/arbiter-request-listing 0)) 1))

    (let [[arbiter-request] (job/arbiter-request-listing 0)]
      (is (true? (:arbiter-request/is-employer-request? arbiter-request)))))

  (testing "Adding and updating work contract."
    (is (= (count (job/work-contract-listing 0)) 0))


    (job/create-work-contract! {:job/index 0
                                :work-contract/index 0
                                :work-contract/contract-status ::enum.status/initial
                                :work-contract/candidate-address "0x0"
                                :work-contract/date-created 7
                                :work-contract/date-updated 7
                                :work-contract/date-finished 0})

    (is (= (job/work-contract-count 0) 1))

    (let [[work-contract] (job/work-contract-listing 0)]
      (is (= (:work-contract/contract-status work-contract) ::enum.status/initial)))

    ;; Update the work contract
    (job/update-work-contract! {:job/index 0
                                :work-contract/index 0
                                :work-contract/contract-status ::enum.status/request-candidate-invite
                                :work-contract/date-created 7
                                :work-contract/date-updated 7})
    
    (let [[work-contract] (job/work-contract-listing 0)]
      (is (= (:work-contract/contract-status work-contract) ::enum.status/request-candidate-invite))))

  (testing "Testing User Feedback on specific work contracts"
    (is (= (count (feedback/feedback-listing 0 0)) 0))

    (feedback/create-feedback!
     {:job/index 0
      :work-contract/index 0
      :feedback/index 0
      :feedback/to-user-type ::enum.user-type/candidate
      :feedback/to-user-id 1
      :feedback/from-user-type ::enum.user-type/employer
      :feedback/from-user-id 2
      :feedback/date-created 1
      :feedback/date-updated 1
      :feedback/rating 4
      :feedback/text "Did an outstanding job!"})

    (is (= (count (feedback/feedback-listing 0 0)) 1))

    (let [feedback-data (-> (feedback/feedback-listing 0 0) first)]
      (is (= (:feedback/text feedback-data) "Did an outstanding job!"))
      (is (= (:feedback/rating feedback-data) 4)))

    (feedback/update-feedback!
     {:job/index 0
      :work-contract/index 0
      :feedback/index 0
      :feedback/to-user-type ::enum.user-type/candidate
      :feedback/to-user-id 1
      :feedback/from-user-type ::enum.user-type/employer
      :feedback/from-user-id 2
      :feedback/date-created 1
      :feedback/date-updated 1
      :feedback/rating 5
      :feedback/text "Did an outstanding job!!"})

    (is (= (count (feedback/feedback-listing 0 0)) 1))

    (let [feedback-data (-> (feedback/feedback-listing 0 0) first)]
      (is (= (:feedback/text feedback-data) "Did an outstanding job!!"))
      (is (= (:feedback/rating feedback-data) 5)))))