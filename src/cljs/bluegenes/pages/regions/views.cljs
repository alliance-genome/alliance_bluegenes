(ns bluegenes.pages.regions.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as reagent]
            [reagent.dom :as dom]
            [bluegenes.pages.regions.graphs :as graphs]
            [bluegenes.components.table :as table]
            [bluegenes.components.loader :refer [loader]]
            [bluegenes.pages.regions.events]
            [bluegenes.pages.regions.subs]
            [bluegenes.pages.regions.results :refer [results-section]]
            [bluegenes.components.imcontrols.views :as im-controls]
            [bluegenes.components.bootstrap :refer [poppable]]
            [bluegenes.components.icons :refer [icon]]
            [clojure.string :as str]
            [oops.core :refer [oget ocall]]))

(def css-transition-group
  (reagent/adapt-react-class js/ReactTransitionGroup.CSSTransitionGroup))

(defn dropdown-hover [{:keys [data children]}]
  [:span.dropdown.dropdown-hover
   [:a.dropdown-toggle
    {:data-toggle "dropdown"
     :role "button"
     :on-click #(.stopPropagation %)}
    children]
   [:div.dropdown-menu.report-item-description
    [:form {:on-submit #(.preventDefault %)
            :on-click #(.stopPropagation %)}
     data]]])

(defn link [text url]
  [:a {:href url :target "_blank"} text])

(def region-search-help
  [:div.region-help
   [:p "Genome regions in the following formats are accepted:"
    [:ul
     [:li [:strong "chromosome:start..end"] ", e.g. 2L:11334..12296"]
     [:li [:strong "chromosome:start-end"] ", e.g. 2R:5866746-5868284 or chrII:14646344-14667746"]
     [:li [:strong "chromosome:start:end:strand"] ", e.g. 3R:2578486:2580016:-1 or 2L:14615455:14619002:1"]
     [:li [:strong "tab delimited"]]]]
   [:p "Both " [:strong "base coordinate"] " (e.g. " [link "BLAST" "https://www.ncbi.nlm.nih.gov/BLAST/blastcgihelp.shtml#get_subsequence"] ", " [link "GFF/GFF3" "http://www.sequenceontology.org/gff3.shtml"] ") and " [:strong "interbase coordinate"] " (e.g. " [link "UCSC BED" "http://genome.ucsc.edu/FAQ/FAQformat#format1"] ", " [link "Chado" "http://gmod.org/wiki/Introduction_to_Chado#Interbase_Coordinates"] ") systems are supported, e.g. for a DNA piece " [:strong "GCCATGTA"] ", the position of the " [:strong "ATG"] " in interbase is [3, 6], and in base coordinates is [4, 6]. Users need to explicitly select one. By default, the base coordinate is selected."]
   [:p "Each genome region needs to take a "  [:strong "new line"] "."]])

(defn non-empty-classes
  "Given an intermine model (or submodel), only keep classes that have data"
  [model]
  (->> model
       (filter (comp pos? :count second))
       (sort-by (comp :displayName second))))

(defn feature-branch
  "Recursively building a tree of user-selectable features as checkboxes"
  []
  (let [settings (subscribe [:regions/settings])]
    (fn [[class-kw {:keys [displayName descendants count] :as n}]]
      [:li {:class (if (class-kw (:feature-types @settings)) "selected")
            :on-click (fn [e]
                        (.stopPropagation e)
                        (dispatch [:regions/toggle-feature-type n]))}
       (if (class-kw (:feature-types @settings))
         [:svg.icon.icon-checkbox-checked [:use {:xlinkHref "#icon-checkbox-checked"}]]
         [:svg.icon.icon-checkbox-unchecked [:use {:xlinkHref "#icon-checkbox-unchecked"}]])
       (str displayName)
       (when-not (empty? descendants)
         (into [:ul.features-tree]
               (map (fn [d] [feature-branch d]) (non-empty-classes descendants))))])))

(defn feature-types-tree
  "UI component for checkbox features selection"
  []
  (let [known-feature-types (subscribe [:regions/sequence-feature-types])
        settings (subscribe [:regions/settings])]
    (fn []
      (into [:ul.features-tree]
            (map (fn [f] [feature-branch f]) (non-empty-classes @known-feature-types))))))

(def coordinate-systems
  ^{:doc "Supported genomic coordinate systems. First element is the default."}
  [:base :interbase])

(defn coordinate-system-selection
  "UI component allowing user to choose which genomic coordinate system. Defaults to base."
  []
  (let [settings (subscribe [:regions/settings])]
    (fn []
      (into [:div.radio-group
             [:label "Coordinates"]]
            (for [coord-kw coordinate-systems]
              [:label.radio-inline
               [:input {:type "radio"
                        :name "coordinates"
                        :value (name coord-kw)
                        :checked (= coord-kw (or (:coordinates @settings)
                                                 (first coordinate-systems)))
                        :on-change #(dispatch [:regions/set-coordinates coord-kw])}]
               [:span.circle]
               [:span.check]
               (name coord-kw)])))))

(def strand-specific-help
  [:div
   [:p "Perform a strand-specific region search (search " [:strong "+"] " strand if region start<end; search " [:strong "–"] " strand if region end<start). Regions that explicitly specify strand using the " [:strong "chromosome:start:end:strand"] " notation will override this behaviour."]
   [:p [:em "Note: Not all features have strand information, so this will lead to fewer results."]]])

(defn strand-specific-selection
  "UI component allowing user to choose to perform a strand-specific region search. Defaults to off."
  []
  (let [settings (subscribe [:regions/settings])]
    (fn []
      [:div.togglebutton
       [:label "Strand-specific"
        [:input {:type "checkbox"
                 :checked (true? (:strand-specific @settings))
                 :on-change #(dispatch [:regions/toggle-strand-specific])}]
        [:span.toggle]
        [dropdown-hover
         {:data strand-specific-help
          :children [icon "question"]}]]])))

(defn organism-selection
  "UI component allowing user to choose which organisms to search. Defaults to all."
  []
  (let [settings (subscribe [:regions/settings])]
    (fn []
      [:div.organism-selection
       [:label "Organism"]
       [im-controls/organism-dropdown
        {:selected-value (if-let [sn (get-in @settings [:organism :shortName])]
                           sn
                           "All Organisms")
         :on-change (fn [organism]
                      (dispatch [:regions/set-selected-organism organism]))}]])))

; Input box for regions
(defn region-input-box
  "UI component allowing user to type in the regions they wish to search for"
  []
  (reagent/create-class
   (let [to-search (subscribe [:regions/to-search])
         results (subscribe [:regions/results])]
     {:reagent-render
      (fn []
        [:textarea.form-control
         {:rows (if @results 3 6)
          :placeholder "Type genome regions here, or click [SHOW EXAMPLE] below."
          :value @to-search
          :on-change (fn [e]
                       (dispatch [:regions/set-to-search (oget e "target" "value")]))}])
      :component-did-mount (fn [this] (.focus (dom/dom-node this)))})))

(defn clear-textbox
  "Interactive UI component to clear any entered text present in the region input textarea."
  []
  (let [to-search (subscribe [:regions/to-search])]
    (fn []
      [css-transition-group
       {:transition-name "fade"
        :transition-enter-timeout 2000
        :transition-leave-timeout 2000
        :component "div"}
       (when @to-search
         [:div.clear-textbox
          ;;this is a fancy x-like character, aka &#10006; - NOT just x
          {:on-click #(dispatch [:regions/set-to-search nil])
           :title "Clear this textbox"} "✖"])])))

(defn region-input []
  [:div.region-input
   [:label "Regions to search "
    [dropdown-hover
     {:data region-search-help
      :children [icon "question"]}]]
   [:div.region-text
    [clear-textbox]
    [region-input-box]]])

(defn checkboxes
  "UI component ot allow user to select which types of overlapping features to find"
  [to-search settings]
  (let [all-selected? (subscribe [:regions/sequence-feature-type-all-selected?])
        results (subscribe [:regions/results])]
    (fn [to-search settings]
      [:div.checkboxes
       [:label
        [:svg.icon
         {:title (if @all-selected? "Deselect all" "Select all")
          :on-click (if @all-selected?
                      #(dispatch [:regions/deselect-all-feature-types])
                      #(dispatch [:regions/select-all-feature-types]))}
         (if @all-selected?
           [:svg.icon.icon-checkbox-checked [:use {:xlinkHref "#icon-checkbox-checked"}]]
           [:svg.icon.icon-checkbox-unchecked [:use {:xlinkHref "#icon-checkbox-unchecked"}]])] "Features to include"]
       ;; having the container around the tree is important because the tree is recursive
       ;; and we know for sure that the container is the final parent! :)
       [:div.feature-tree-container
        {:class (when @results :shrinkified)}
        [feature-types-tree]]])))

(defn input-section
  "Entire UI input section / top half of the region search"
  []
  (let [settings (subscribe [:regions/settings])
        to-search (subscribe [:regions/to-search])
        search-example (subscribe [:regions/example-search])]
    (fn []
      [:div.input-section
       ; Parameters section
       [:div.organism-and-regions
        [region-input]
        [coordinate-system-selection]
        [strand-specific-selection]
        [organism-selection]
        (let [example-text @search-example]
          [:div.btn-group.action-buttons
           [:button.btn.btn-default.btn-raised.btn-block
            {:disabled (empty? example-text)
             :title (when (empty? example-text) "No example available")
             :on-click #(dispatch [:regions/set-to-search (str/replace example-text "\\n" "\n")])}
            "Show Example"]
           [:button.btn.btn-primary.btn-raised.btn-block
            {:disabled (or (str/blank? @to-search)
                           (empty? (filter (fn [[name enabled?]] enabled?) (:feature-types @settings))))
             :on-click (fn [e] (dispatch [:regions/run-query])
                         (ocall (oget e "target") "blur"))
             :title "Enter something into the 'Regions to search' box or click on [SHOW EXAMPLE]"}
            "Search"]])]
       ; Results section
       [checkboxes to-search settings]])))

(defn main []
  (reagent/create-class
   {:component-did-mount #(dispatch [:regions/select-all-feature-types])
    :reagent-render
    (fn []
      [:div.container.regionsearch
       [input-section]
       [results-section]])}))
