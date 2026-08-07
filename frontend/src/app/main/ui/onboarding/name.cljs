;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.ui.onboarding.name
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.schema :as sm]
   [app.main.data.profile :as du]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.components.forms :as fm]
   [app.util.i18n :refer [tr]]
   [rumext.v2 :as mf]))

(def ^:private schema:name-form
  [:map {:title "OnboardingNameForm"}
   [:fullname [::sm/text {:min 1 :max 250}]]])

(mf/defc name-modal
  []
  (let [profile (mf/deref refs/profile)
        initial (mf/with-memo [(:fullname profile)]
                  {:fullname (:fullname profile)})
        form    (fm/use-form :schema schema:name-form
                             :initial initial)

        on-submit
        (mf/use-fn
         (mf/deps form profile)
         (fn [_form _event]
           (let [fullname (:fullname (:clean-data @form))]
             (st/emit! (du/update-profile (assoc profile :fullname fullname))
                       (du/persist-profile)
                       (du/mark-onboarding-as-viewed)))))]

    [:div {:class (stl/css-case :modal-overlay true)}
     [:div {:class (stl/css :modal-container)}
      [:h1 {:class (stl/css :modal-title)}
       (tr "onboarding.questions.lets-get-started")]

      [:& fm/form {:form form
                   :on-submit on-submit
                   :class (stl/css :form-wrapper)}
       [:& fm/input
        {:type "text"
         :name :fullname
         :label (tr "dashboard.your-name")
         :auto-focus true}]

       [:div {:class (stl/css :action-buttons)}
        [:> fm/submit-button*
         {:label (tr "labels.start")
          :class (stl/css :next-button)}]]]]]))
