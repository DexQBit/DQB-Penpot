;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.util.platform-admin
  "Helpers for PENPOT_ADMINS / :admins platform-admin checks."
  (:require
   [app.common.exceptions :as ex]
   [app.common.logging :as l]
   [app.common.uuid :as uuid]
   [app.config :as cf]
   [app.db :as db]
   [app.rpc.commands.profile :as profile]
   [app.rpc.permissions :as perms]
   [cuerdas.core :as str]))

(defn admin-emails
  []
  (or (cf/get :admins) #{}))

(defn platform-admin-email?
  [email]
  (when email
    (contains? (admin-emails) (str/lower (str email)))))

(defn platform-admin-profile?
  "True when profile map has :is-admin or its :email is in :admins."
  [profile]
  (or (true? (:is-admin profile))
      (platform-admin-email? (:email profile))))

(defn mark-platform-admin
  "Assoc :is-admin on a profile map from PENPOT_ADMINS."
  [profile]
  (assoc profile :is-admin (boolean (platform-admin-email? (:email profile)))))

(defn get-profile-email
  [conn profile-id]
  (:email (db/get* conn :profile {:id profile-id}
                   {:columns [:email]
                    ::db/remove-deleted false})))

(defn platform-admin-id?
  [conn profile-id]
  (platform-admin-email? (get-profile-email conn profile-id)))

(defn check-platform-admin!
  "Raise if profile-id is not a platform admin."
  [conn profile-id]
  (when-not (platform-admin-id? conn profile-id)
    (ex/raise :type :not-found
              :code :object-not-found
              :hint "not allowed")))

(defn ensure-not-platform-admin-target!
  "Raise if the target profile is a platform admin (cannot remove/downgrade)."
  [conn target-profile-id]
  (when (platform-admin-id? conn target-profile-id)
    (ex/raise :type :validation
              :code :platform-admin-protected
              :hint "platform admins cannot be removed or downgraded")))

(defn- team-member?
  [conn team-id profile-id]
  (some? (db/get* conn :team-profile-rel
                  {:team-id team-id :profile-id profile-id}
                  {::db/remove-deleted false})))

(defn add-platform-admins-to-team!
  "Add every PENPOT_ADMINS profile as owner on the given team (idempotent)."
  [{:keys [::db/conn] :as cfg} team-id]
  (doseq [email (admin-emails)]
    (when-let [admin (profile/get-profile-by-email conn email)]
      (when-not (team-member? conn team-id (:id admin))
        (db/insert! conn :team-profile-rel
                    (assoc (perms/assign-role-flags
                            {:team-id team-id :profile-id (:id admin)}
                            :owner)
                           :id (uuid/next)))
        (l/info :hint "auto-added platform admin to team"
                :team-id (str team-id)
                :email email)))))

(defn backfill-platform-admins!
  "Add all platform admins as owners on every non-deleted team."
  [{:keys [::db/conn] :as cfg}]
  (let [teams (db/exec! conn ["select id from team where deleted_at is null"])]
    (doseq [{:keys [id]} teams]
      (add-platform-admins-to-team! cfg id))
    (count teams)))
