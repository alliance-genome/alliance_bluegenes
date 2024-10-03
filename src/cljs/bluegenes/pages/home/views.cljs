(ns bluegenes.pages.home.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [bluegenes.route :as route]
            [bluegenes.components.icons :refer [icon icon-comp]]
            [bluegenes.components.navbar.nav :refer [mine-icon]]
            [bluegenes.components.search.typeahead :as search]
            [clojure.string :as str]
            [bluegenes.utils :refer [ascii-arrows ascii->svg-arrows md-paragraph md-element
                                     get-mine-ns get-mine-url]]
            [goog.string :as gstring]
            [cljs-time.format :as time-format]
            [cljs-time.coerce :as time-coerce]
            [oops.core :refer [oget]]
            [bluegenes.components.bootstrap :refer [poppable]]
            [bluegenes.config :refer [server-vars]]))

(defn mine-intro []
  (let [mine-name @(subscribe [:current-mine-human-name])
        description @(subscribe [:current-mine/description])
        notice @(subscribe [:current-mine/notice])
        release @(subscribe [:current-mine/release])]
    [:div.row.section.mine-intro
     [:div.col-xs-10.col-xs-offset-1
      (when-not @(subscribe [:home/customisation :hideMineIntro])
        [:<>
         [:h1.text-center.mine-name mine-name]
         [:div.mine-release
          ;; Only prepend 'v' if release starts with a digit.
          (cond->> release
            (some->> release (re-find #"^\d")) (str "v"))]
         [:div.mine-description
          (md-paragraph description)]])
      (when notice
        [:div.well.well-sm.well-warning.text-center
         [md-element notice]])
      (when-not @(subscribe [:home/customisation :hideSearch])
        [:div.search
         [search/main]
         [:div.search-info
          [icon "info"]
          [:span "Genes, proteins, pathways, ontology terms, authors, etc."]]])]]))

(defn template-queries []
  (let [categories @(subscribe [:templates-by-popularity/all-categories])
        current-category (or @(subscribe [:home/active-template-category])
                             (first categories))
        templates @(subscribe [:templates-by-popularity/category current-category])]
    [:div.row.section
     [:div.col-xs-12
      [:h2.text-center "Go by Most Popular Queries"]]
     [:div.col-xs-12.template-preview
      [:ul.nav.nav-tabs.template-tabs
       (doall
        (for [category categories]
          ^{:key category}
          [:li {:class (when (= category current-category) "active")}
           [:a {:on-click #(dispatch [:home/select-template-category category])}
            (str/replace category #"^im:aspect:" "")]]))]
      [:ul.template-list
       (doall
        (for [{:keys [title name]} templates]
          ^{:key name}
          [:li
           (into [:a {:href (route/href ::route/template {:template name})}]
                 (if (ascii-arrows title)
                   (ascii->svg-arrows title)
                   [[:span title]]))]))]
      [:a.more-queries {:href (route/href ::route/templates)}
       "More queries here"]]]))

(def post-time-formatter (time-format/formatter "MMMM d, Y"))

(defn latest-news []
  (let [posts (take 3 (or @(subscribe [:home/latest-posts]) nil))]
    (if (empty? posts)
      [:p "Latest news from the InterMine community."]
      (into [:ul.latest-news]
            (for [{:keys [title link pubDate description]} posts]
              [:li
               [:span (time-format/unparse post-time-formatter
                                           (time-coerce/from-string pubDate))]
               [:a {:href link :target "_blank"} title]
               [:p (-> description gstring/unescapeEntities str/trim (subs 0 100))]])))))

(defn convert-custom-cta
  "Convert the custom CTA format used in web.properties to the one used for rendering."
  [{:keys [label text]
    route-value :route dispatch-value :dispatch url-value :url}]
  {:label label
   :props (cond
            route-value {:href (route/href (keyword "bluegenes.route" route-value))}
            dispatch-value {:on-click #(dispatch [(keyword dispatch-value)])
                            :role "button"}
            url-value {:href url-value
                       :target "_blank"})
   :body (md-paragraph text)})

;; This MUST be a function, as otherwise `route/href` would get invoked when
;; the namespace is required, which depends on a subscription which might not
;; be registered in time, causing a crash.
(defn default-cta []
  [{:label "Analyse data"
    :props {:href (route/href ::route/upload)}
    :body [:p [:strong "Upload"] " your own sets of genes, proteins, transcripts or other data type to analyse against the integrated data."]}
   {:label "Browse sources"
    :props {:on-click #(dispatch [:home/query-data-sources])
            :role "button"}
    :body [:p "Browse the full set of data available including versions, publications and links to the " [:strong "original data"] "."]}
   {:label "Build your own query"
    :props {:href (route/href ::route/querybuilder)}
    :body [:p "The " [:strong "Query Builder"] " allows you to select and combine data classes, apply filters and configure the result table.  For a full set of pre-built searches, see the " [:a {:href (route/href ::route/templates)} "Templates"] "."]}
   {:label "Tutorials"
    :props {:href "http://intermine.org/intermine-user-docs/"
            :target "_blank"}
    :body [:p "Learn more about InterMine and how to search and analyse the data with a comprehensive set of " [:strong "written and video tutorials"] ".  Please " [:a {:on-click #(dispatch [:home/scroll-to-feedback]) :role "button"} "contact us"] " if you can’t find what you need!"]}
   {:label "Web services"
    :props {:href "http://intermine.org/im-docs/docs/web-services/index"
            :target "_blank"}
    :body [:p "The " [:strong "InterMine API"] " has language bindings for Perl, Python, Ruby and Java, allowing you to easily run queries directly from scripts.  All queries available in the user interface can also be run through the API with results being returned in a number of formats."]}
   {:label "Submit feedback"
    :props {:on-click #(dispatch [:home/scroll-to-feedback])
            :role "button"}
    :body [:p [:strong "Contact us"] " with problems, comments, suggestions and any other queries."]}
   {:label "What's new"
    :props {:href (or @(subscribe [:current-mine/news]) "https://intermineorg.wordpress.com/")
            :target "_blank"}
    :body [latest-news]}
   {:label "Cite us"
    :props {:href @(subscribe [:current-mine/citation])
            :target "_blank"}
    :body [:p "Please help us to maintain funding: if we have helped your research please remember to cite us in your publications."]}])

(defn call-to-action []
  (let [custom-cta @(subscribe [:home/custom-cta])
        cta (if (seq custom-cta)
              (map convert-custom-cta custom-cta)
              (default-cta))]
    (into [:div.row.section.grid] ;; Without grid class the 3rd row won't be on the same row.
           ;; This isn't official bootstrap, so I can only imagine Gridlex is messing with things.
          (for [[index {:keys [label props body]}] (map-indexed vector cta)]
            [:div.col-xs-12.col-sm-5.cta-block
             {:class (when (odd? index) :col-sm-offset-2)}
             [:a.btn.btn-home props label]
             body]))))

(defn mine-selector-filter []
  (let [all-neighbourhoods @(subscribe [:home/all-registry-mine-neighbourhoods])
        current-neighbourhood (or @(subscribe [:home/active-mine-neighbourhood])
                                  (first all-neighbourhoods))]
    (into [:div.mine-neighbourhood-filter.text-center]
          (for [neighbourhood all-neighbourhoods]
            [:label
             [:input {:type "radio"
                      :name neighbourhood
                      :checked (= neighbourhood current-neighbourhood)
                      :on-change #(dispatch [:home/select-mine-neighbourhood neighbourhood])}]
             neighbourhood]))))

(defn get-fg-color [mine-details]
  (or (get-in mine-details [:colors :header :text])
      (get-in mine-details [:branding :colors :header :text])))

(defn get-bg-color [mine-details]
  (or (get-in mine-details [:colors :header :main])
      (get-in mine-details [:branding :colors :header :main])))

(defn mine-selector-entry [[mine-key details] & {:keys [active?]}]
  (let [{:keys [name]} details]
    [:button.btn-link.col-xs-6.col-md-4.col-lg-3.mine-entry
     (merge
      {:class (when active? "mine-entry-active")
       :on-click #(dispatch [:home/select-preview-mine mine-key])}
      (when active?
        {:style {:color (get-fg-color details)
                 :background-color (get-bg-color details)}}))
     [:span (or name "default")]
     [icon "plus" nil [:pull-right]]]))

(defn mine-selector-preview []
  (let [{:keys [description name] :as preview-mine} @(subscribe [:home/preview-mine])
        mine-ns (get-mine-ns preview-mine)
        external? (or (:external? preview-mine)
                      (contains? @(subscribe [:registry-external]) mine-ns))]
    [:div.col-xs-10.col-xs-offset-1.col-sm-offset-0.col-sm-3.mine-preview
     {:style {:color (get-fg-color preview-mine)
              :background-color (get-bg-color preview-mine)}}
     [:h4.text-center name]
     (md-paragraph description)
     [:div.preview-image
      [mine-icon preview-mine :class "img-responsive"]]
     (if external?
       [poppable
        {:data (if (:external? preview-mine)
                 "This mine has been configured to open in a new tab."
                 "This mine will open in a new tab as it's incompatible due to either running an InterMine API version below 27 or being only available through unsecured HTTP when BlueGenes is accessed through HTTPS.")
         :options {:data-placement "bottom"}
         :children [:a.btn.btn-block
                    {:target "_blank"
                     :href (get-mine-url preview-mine)
                     :style {:fill (get-fg-color preview-mine)
                             :color (get-fg-color preview-mine)
                             :background-color (get-bg-color preview-mine)}}
                    (str "Open " name)
                    [icon "external"]]}]
       [:button.btn.btn-block
        {:on-click (fn [_]
                     (dispatch [::route/navigate ::route/home {:mine mine-ns}])
                     (dispatch [:scroll-to-top]))
         :style {:color (get-fg-color preview-mine)
                 :background-color (get-bg-color preview-mine)}}
        (str "Switch to " name)])]))

(defn mine-selector []
  (let [mines @(subscribe [:home/mines-by-neighbourhood])
        active-ns (get-mine-ns @(subscribe [:home/preview-mine]))]
    [:div.row.section
     [:div.col-xs-12
      [:h2.text-center "InterMine for all"]]
     [:div.col-xs-12.mine-selector
      [mine-selector-filter]
      [:div.row.mine-selector-body
       [:div.col-xs-12.col-sm-9.mine-selector-entries
        [:div.row
         (for [mine mines]
           ^{:key (key mine)}
           [mine-selector-entry mine :active? (= active-ns (key mine))])]]
       [mine-selector-preview]]]]))

(defn external-tools []
  [:div.row.section
   [:div.col-xs-12
    [:h2.text-center "Alternative tools"]]
   [:div.col-xs-12.col-sm-5.cta-block
    [:a.btn.btn-home
     {:href "http://data-browser.apps.intermine.org/"
      :target "_blank"}
     "Data Browser"]
    [:p "A " [:strong "faceted search tool"] " to display the data from InterMine database, allowing the users to search easily within the different mines available around InterMine without the requirement of having an extensive knowledge of the data model."]]
   [:div.col-xs-12.col-sm-5.col-sm-offset-2.cta-block
    [:a.btn.btn-home
     {:href "http://gointermod.apps.intermine.org/"
      :target "_blank"}
     "InterMOD Gene Ontology"]
    [:p "This tool searches for homologous genes and " [:strong "associated GO terms"] " across six model organisms (yeast, nematode worm, fruit fly, zebrafish, mouse, rat) and humans, with a heatmap, statistical enrichment, and a GO term relationship graph."]]])

(defn feedback []
  (let [email* (r/atom "")
        text* (r/atom "")]
    (fn []
      (let [{:keys [type message error]} @(subscribe [:home/feedback-response])]
        [:div.row.section
         [:div.col-xs-12
          [:h2.text-center "We value your opinion"]]
         (case type
           :success [:div.feedback-response
                     [icon "checkmark"]
                     [:h3 "Thank you!"]
                     [:p "Your feedback has been submitted."]]
           [:div.col-xs-12.col-sm-10.col-sm-offset-1.col-md-8.col-md-offset-2.feedback
            [:input.form-control
             {:type "email"
              :placeholder "Your email (optional)"
              :value @email*
              :on-change #(reset! email* (oget % :target :value))}]
            [:textarea#feedbackform.form-control
             {:placeholder "Your feedback here"
              :rows 5
              :value @text*
              :on-change #(reset! text* (oget % :target :value))}]
            [:button.btn.btn-block
             {:on-click #(dispatch [:home/submit-feedback @email* @text*])}
             "Submit"]
            (when (or message error)
              [:p.failure.text-center message
               (when error
                 [:code error])])])]))))

(defn credits-entry [{:keys [text image url]}]
  (if (not-empty text)
    [:div.col-xs-12
     [:div.row.row-center-cols
      [:div.col-xs-4
       [:a {:href url :target "_blank"}
        (if (string? image)
          [:img.img-responsive
           {:src image}]
          image)]]
      [:div.col-xs-8
       (md-paragraph text)]]]
    [:div.col-xs-4
     [:a {:href url :target "_blank"}
      (if (string? image)
        [:img.img-responsive
         {:src image}]
        image)]]))

(def credits-intermine
  [{:text "The [InterMine](http://intermine.org/) integrated data warehouse has been developed principally through support of the [Wellcome Trust](https://wellcome.ac.uk/). Complementary projects have been funded by the [NIH/NHGRI](https://www.nih.gov/) and the [BBSRC](https://bbsrc.ukri.org/)."
    :image (str (:bluegenes-deploy-path @server-vars) "/images/intermine-logo.png")
    :url "http://intermine.org/"}
   {:text "The [BlueGenes](https://github.com/intermine/bluegenes) frontend for InterMine has been developed through the support of the [Wellcome Trust](https://wellcome.ac.uk/)."
    :image [icon-comp "bluegenes-logo-text"
            :style {:width 240 :height "auto"}]
    :url "http://intermine.org/"}])

(defn credits-fallback []
  (let [mine-name @(subscribe [:current-mine-human-name])
        current-mine @(subscribe [:current-mine-name])
        {:keys [maintainerUrl maintainerOrgName]}
        (get @(subscribe [:registry]) current-mine)]
    (when (not= maintainerOrgName "InterMine")
      [:h4.credits-fallback
       [:a {:href maintainerUrl
            :target "_blank"}
        (if (empty? maintainerOrgName)
          (str "The maintainers and funders of " mine-name)
          (str maintainerOrgName " and its funders"))]])))

(defn credits []
  (let [mine-name @(subscribe [:current-mine-human-name])
        entries @(subscribe [:current-mine/credits])
        all-entries (concat entries credits-intermine)]
    [:div.row.section
     [:div.col-xs-12
      [:h2.text-center (str mine-name " is made possible by")]]
     (when (empty? entries)
       [:div.col-xs-12.text-center
        [credits-fallback]])
     [:div.col-xs-10.col-xs-offset-1
      (into [:div.row.row-center-cols.row-space-cols]
            (for [entry all-entries]
              [credits-entry entry]))]]))

(defn main []
  [:div.container.home
   [mine-intro]
   [template-queries]
   [call-to-action]
   [mine-selector]
   [external-tools]
   [feedback]
   [credits]])
   (when-not @(subscribe [:home/customisation :hideTemplateQueries]) [template-queries])
   (when-not @(subscribe [:home/customisation :hideCTA]) [call-to-action])
   (when-not @(subscribe [:home/customisation :hideMineSelector]) [mine-selector])
   (when-not @(subscribe [:home/customisation :hideAlternativeTools]) [external-tools])
   (when-not @(subscribe [:home/customisation :hideFeedback]) [feedback])
   (when-not @(subscribe [:home/customisation :hideCredits]) [credits])])
