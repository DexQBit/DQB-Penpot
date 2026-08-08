;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.rpc.commands.files-share
  "Share link related rpc mutation methods."
  (:require
   [app.binfile.common :as bfc]
   [app.common.exceptions :as ex]
   [app.common.schema :as sm]
   [app.common.time :as ct]
   [app.common.uuid :as uuid]
   [app.db :as db]
   [app.rpc :as-alias rpc]
   [app.rpc.doc :as-alias doc]
   [app.util.platform-admin :as platform-admin]
   [app.util.services :as sv]))

(defn- check-platform-admin-file-access!
  [conn profile-id file-id]
  (platform-admin/check-platform-admin! conn profile-id)
  (let [perms (bfc/get-file-permissions conn profile-id file-id)]
    (when-not (or (:is-owner perms) (:is-admin perms) (:can-edit perms))
      (ex/raise :type :not-found
                :code :object-not-found
                :hint "not found"))))

;; --- MUTATION: Create Share Link

(declare create-share-link)

(def ^:private schema:create-share-link
  [:map {:title "create-share-link"}
   [:file-id ::sm/uuid]
   [:who-comment [:string {:max 250}]]
   [:who-inspect [:string {:max 250}]]
   [:pages [:set ::sm/uuid]]
   [:expires-at {:optional true} [:maybe ::ct/inst]]])

(sv/defmethod ::create-share-link
  "Creates a share-link object.

  Share links are resources that allows external users access to specific
  pages of a file with specific permissions (who-comment and who-inspect)."
  {::doc/added "1.18"
   ::doc/module :files
   ::sm/params schema:create-share-link
   ::db/transaction true}
  [{:keys [::db/conn]} {:keys [::rpc/profile-id file-id] :as params}]
  (check-platform-admin-file-access! conn profile-id file-id)
  (create-share-link conn (assoc params :profile-id profile-id)))

(defn create-share-link
  [conn {:keys [profile-id file-id pages who-comment who-inspect expires-at]}]
  (let [pages (db/create-array conn "uuid" pages)
        slink (db/insert! conn :share-link
                          (cond-> {:id (uuid/next)
                                   :file-id file-id
                                   :who-comment who-comment
                                   :who-inspect who-inspect
                                   :pages pages
                                   :owner-id profile-id}
                            (some? expires-at)
                            (assoc :expires-at expires-at)))]

    (update slink :pages db/decode-pgarray #{})))

;; --- MUTATION: Delete Share Link

(def ^:private schema:delete-share-link
  [:map {:title "delete-share-link"}
   [:id ::sm/uuid]])

(sv/defmethod ::delete-share-link
  {::doc/added "1.18"
   ::doc/module ::files
   ::sm/params schema:delete-share-link
   ::db/transaction true}
  [{:keys [::db/conn]} {:keys [::rpc/profile-id id] :as params}]
  (let [slink (db/get-by-id conn :share-link id)]
    (check-platform-admin-file-access! conn profile-id (:file-id slink))
    (db/delete! conn :share-link {:id id})
    nil))

;; --- QUERY: Get File Share Links

(def ^:private schema:get-file-share-links
  [:map {:title "get-file-share-links"}
   [:file-id ::sm/uuid]])

(sv/defmethod ::get-file-share-links
  {::doc/added "1.18"
   ::doc/module :files
   ::sm/params schema:get-file-share-links}
  [{:keys [::db/conn]} {:keys [::rpc/profile-id file-id]}]
  (check-platform-admin-file-access! conn profile-id file-id)
  (->> (db/query conn :share-link {:file-id file-id})
       (mapv (fn [row]
               (-> row
                   (update :pages db/decode-pgarray #{})
                   (dissoc :flags))))))
