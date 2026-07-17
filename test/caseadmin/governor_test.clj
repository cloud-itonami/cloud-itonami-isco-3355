(ns caseadmin.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [caseadmin.store :as store]
            [caseadmin.advisor :as advisor]
            [caseadmin.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-officer! st {:officer-id "O-1" :name "Insp. Rivera"
                                 :precinct-id "precinct-9" :verified? true})
    (store/register-case! st {:case-id "C-1" :precinct-id "precinct-9"
                              :max-supply-cost 500 :verified? true})
    st))

(defn- log-op []
  {:op :log-case-file-record :effect :propose :officer-id "O-1" :case-id "C-1"
   :item-id "EV-1" :custodian "Insp. Rivera" :timestamp "2026-07-14T10:00:00Z"
   :chain-of-custody ["Insp. Rivera"] :stake :low :confidence 0.9
   :rationale "documented log-case-file-record for case C-1"})

(defn- interview-op []
  {:op :schedule-interview-appointment :effect :propose :officer-id "O-1" :case-id "C-1"
   :interviewee-role :witness :proposed-time "2026-07-20T09:00:00Z"
   :location "precinct-9 interview room 2" :stake :low :confidence 0.9
   :rationale "documented schedule-interview-appointment for case C-1"})

(defn- flag-op
  ([] (flag-op nil))
  ([case-id]
   {:op :flag-investigation-review :effect :propose :officer-id "O-1" :case-id case-id
    :reason :new-case :note "new case intake needs assignment" :stake :low :confidence 0.9
    :rationale "documented flag-investigation-review for case (no case yet — new-case intake)"}))

(defn- supply-op [cost]
  {:op :coordinate-supply-order :effect :propose :officer-id "O-1" :case-id "C-1"
   :item "fingerprint kit" :cost cost :vendor "ForensicsCo" :stake :low :confidence 0.9
   :rationale "documented coordinate-supply-order for case C-1"})

(def ^:private req {})

;; --- happy path -----------------------------------------------------

(deftest ok-well-formed-log-entry
  (let [st (fresh-store)
        v (governor/check req {} (log-op) st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest ok-well-formed-interview-scheduling
  (let [st (fresh-store)
        v (governor/check req {} (interview-op) st)]
    (is (:ok? v))))

(deftest ok-at-or-below-threshold-supply-order
  (let [st (fresh-store)
        v (governor/check req {} (supply-op 250) st)]
    (is (:ok? v))))

(deftest ok-at-exact-supply-cost-threshold-boundary
  (testing "the supply-cost escalation threshold is inclusive (exactly-at-threshold does not escalate)"
    (let [st (fresh-store)
          v (governor/check req {} (supply-op governor/supply-cost-escalation-threshold) st)]
      (is (:ok? v))
      (is (not (:escalate? v))))))

;; --- officer provenance ----------------------------------------------

(deftest hard-on-unregistered-officer
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :officer-id "ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-officer (:rule %)) (:violations v)))))

(deftest hard-on-unverified-officer
  (let [st (fresh-store)]
    (store/register-officer! st {:officer-id "O-2" :name "Unverified"
                                 :precinct-id "precinct-9" :verified? false})
    (let [v (governor/check req {} (assoc (log-op) :officer-id "O-2") st)]
      (is (:hard? v))
      (is (some #(= :officer-unverified (:rule %)) (:violations v))))))

;; --- case-file provenance ---------------------------------------------

(deftest hard-on-missing-case-id
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :case-id nil) st)]
    (is (:hard? v))
    (is (some #(= :missing-case-id (:rule %)) (:violations v)))))

(deftest hard-on-unknown-case
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :case-id "C-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-case (:rule %)) (:violations v)))))

(deftest hard-on-unverified-case
  (let [st (fresh-store)]
    (store/register-case! st {:case-id "C-2" :precinct-id "precinct-9"
                              :max-supply-cost 500 :verified? false})
    (let [v (governor/check req {} (assoc (log-op) :case-id "C-2") st)]
      (is (:hard? v))
      (is (some #(= :case-unverified (:rule %)) (:violations v))))))

(deftest hard-on-case-wrong-precinct
  (let [st (fresh-store)]
    (store/register-case! st {:case-id "C-3" :precinct-id "precinct-1"
                              :max-supply-cost 500 :verified? true})
    (let [v (governor/check req {} (assoc (log-op) :case-id "C-3") st)]
      (is (:hard? v))
      (is (some #(= :case-wrong-precinct (:rule %)) (:violations v))))))

(deftest flag-investigation-review-does-not-require-existing-case
  (testing "flag-investigation-review is the channel by which a brand-new case is surfaced for human intake"
    (let [st (fresh-store)
          v (governor/check req {} (flag-op nil) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

;; --- no-actuation / closed allowlist ----------------------------------

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-make-arrest
  (testing "no path through this actor can make an arrest — no such op exists in the allowlist to begin with;
            this asserts the governor also rejects one forged onto a proposal"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :make-arrest) st)]
      (is (:hard? v))
      (is (some #(= :op-not-allowed (:rule %)) (:violations v))))))

(deftest hard-on-op-not-allowed-authorize-search
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :authorize-search) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-authorize-seizure
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :authorize-seizure) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-file-formal-charge
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :file-formal-charge) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-determine-guilt
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :determine-guilt) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowed-determine-culpability
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :op :determine-culpability) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowed (:rule %)) (:violations v)))))

(deftest every-scope-excluded-op-name-is-rejected
  (testing "every explicitly named scope-excluded op fixture is a hard, permanent block"
    (let [st (fresh-store)]
      (doseq [op governor/scope-excluded-ops]
        (let [v (governor/check req {} (assoc (log-op) :op op) st)]
          (is (:hard? v) (str "op " op " was not hard-blocked"))
          (is (some #(= :op-not-allowed (:rule %)) (:violations v))
              (str "op " op " did not trip :op-not-allowed")))))))

;; --- evidentiary-interpretation / interview-substance forbidden -------

(deftest hard-on-evidentiary-interpretation-forbidden
  (testing "log-case-file-record is a physical chain-of-custody record only — evidentiary interpretation is forbidden"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :conclusion "suspect is guilty") st)]
      (is (:hard? v))
      (is (some #(= :evidentiary-interpretation-forbidden (:rule %)) (:violations v))))))

(deftest hard-on-evidentiary-interpretation-forbidden-guilt-key
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :guilt :confirmed) st)]
    (is (:hard? v))
    (is (some #(= :evidentiary-interpretation-forbidden (:rule %)) (:violations v)))))

(deftest hard-on-interview-substance-forbidden
  (testing "schedule-interview-appointment never records interview substance or conclusions"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (interview-op) :summary "witness confirmed seeing the suspect") st)]
      (is (:hard? v))
      (is (some #(= :interview-substance-forbidden (:rule %)) (:violations v))))))

(deftest hard-on-interview-substance-forbidden-testimony-key
  (let [st (fresh-store)
        v (governor/check req {} (assoc (interview-op) :testimony "the suspect confessed") st)]
    (is (:hard? v))
    (is (some #(= :interview-substance-forbidden (:rule %)) (:violations v)))))

;; --- scope-excluded rationale (defense-in-depth) -----------------------

(deftest hard-on-scope-excluded-arrest-rationale
  (testing "a proposal on an otherwise-allowed op whose rationale names a finalization action for an arrest
            is a permanent HARD block, independent of the op-allowlist check"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :rationale "logged evidence in order to make the arrest") st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-search-rationale
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :rationale "filed the evidence to authorize the search") st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-charge-rationale
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :rationale "logged the record to file the formal charge") st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-guilt-rationale
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :rationale "logged the entry to determine the suspect's guilt") st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-note-field
  (testing "the scope-exclusion check also inspects :note (used by flag-investigation-review)"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (flag-op "C-1") :note "recommend we make the arrest today") st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded (:rule %)) (:violations v))))))

;; --- escalation ---------------------------------------------------------

(deftest always-escalates-flag-investigation-review-even-at-high-confidence
  (testing "surfacing a case file that needs human detective/inspector review always requires human review"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (flag-op "C-1") :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-above-threshold-supply-order
  (testing "a forensic/office-equipment supply order above the cost threshold always needs human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (supply-op (+ governor/supply-cost-escalation-threshold 1))
                                          :confidence 0.99)
                            st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

;; --- fleet-known self-trip regression -----------------------------------

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own rationale text for every op in the closed allowlist never contains
            a scope-excluded finalization/execution phrase for an arrest, search/seizure authorization, formal
            charge, or guilt/culpability determination (fleet-known self-trip bug class regression)"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          requests [{:op :log-case-file-record :officer-id "O-1" :case-id "C-1" :stake :low
                     :item-id "EV-1" :custodian "Insp. Rivera" :timestamp "2026-07-14T10:00:00Z"}
                    {:op :schedule-interview-appointment :officer-id "O-1" :case-id "C-1" :stake :low
                     :interviewee-role :witness :proposed-time "2026-07-20T09:00:00Z" :location "room 2"}
                    {:op :flag-investigation-review :officer-id "O-1" :case-id "C-1" :stake :low
                     :reason :deadline-approaching :note "statute-of-limitations deadline in 30 days"}
                    {:op :flag-investigation-review :officer-id "O-1" :case-id nil :stake :low
                     :reason :new-case :note "new case intake needs assignment"}
                    {:op :coordinate-supply-order :officer-id "O-1" :case-id "C-1" :stake :low
                     :item "evidence bags" :cost 40 :vendor "OfficeSupplyCo"}]]
      (doseq [req' requests]
        (let [proposal (advisor/-advise adv st req')]
          (is (false? (governor/out-of-scope? proposal))
              (str "op " (:op req') " self-tripped scope-exclusion: " (:rationale proposal)))
          (let [v (governor/check {} {} proposal st)]
            (is (not (contains? (set (map :rule (:violations v))) :scope-excluded))
                (str "op " (:op req') " tripped :scope-excluded in governor/check"))
            (is (not (contains? (set (map :rule (:violations v))) :op-not-allowed))
                (str "op " (:op req') " tripped :op-not-allowed in governor/check"))))))))
