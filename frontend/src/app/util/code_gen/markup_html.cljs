;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.util.code-gen.markup-html
  (:require
   ["react-dom/server" :as rds]
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.pages.helpers :as cph]
   [app.common.types.shape.layout :as ctl]
   [app.config :as cfg]
   [app.main.ui.shapes.text.html-text :as text]
   [app.util.code-gen.common :as cgc]
   [app.util.code-gen.markup-svg :refer [generate-svg]]
   [cuerdas.core :as str]
   [rumext.v2 :as mf]))

(defn svg-markup?
  "Function to determine whether a shape is rendered in HTML+CSS or is rendered
  through a SVG"
  [shape]
  (or
   ;; path and path-like shapes
   (cph/path-shape? shape)
   (cph/bool-shape? shape)

   ;; imported SVG images
   (cph/svg-raw-shape? shape)
   (some? (:svg-attrs shape))

   ;; CSS masks are not enough we need to delegate to SVG
   (cph/mask-shape? shape)

   ;; Texts with shadows or strokes we render in SVG
   (and (cph/text-shape? shape)
        (or (d/not-empty? (:shadow shape))
            (d/not-empty? (:strokes shape))))

   ;; When a shape has several strokes or the stroke is not a "border"
   (or (> (count (:strokes shape)) 1)
       (and (= (count (:strokes shape)) 1)
            (not= (-> shape :strokes first :stroke-alignment) :center)))))

(defn generate-html
  ([objects shape]
   (generate-html objects shape 0))

  ([objects shape level]
   (let [indent (str/repeat "  " level)
         maybe-reverse (if (ctl/any-layout? shape) reverse identity)

         shape-html
         (cond
           (svg-markup? shape)
           (let [svg-markup (generate-svg objects shape)]
             (dm/fmt "%<div class=\"%\">\n%\n%</div>"
                     indent
                     (cgc/shape->selector shape)
                     svg-markup
                     indent))

           (cph/text-shape? shape)
           (let [text-shape-html (rds/renderToStaticMarkup (mf/element text/text-shape #js {:shape shape :code? true}))]
             (dm/fmt "%<div class=\"%\">\n%\n%</div>"
                     indent
                     (cgc/shape->selector shape)
                     text-shape-html
                     indent))

           (cph/image-shape? shape)
           (let [data (or (:metadata shape) (:fill-image shape))
                 image-url (cfg/resolve-file-media data)]
             (dm/fmt "%<img src=\"%\" class=\"%\">\n%</img>"
                     indent
                     image-url
                     (cgc/shape->selector shape)
                     indent))

           (empty? (:shapes shape))
           (dm/fmt "%<div class=\"%\">\n%</div>"
                   indent
                   (cgc/shape->selector shape)
                   indent)

           :else
           (dm/fmt "%<div class=\"%\">\n%\n%</div>"
                   indent
                   (cgc/shape->selector shape)
                   (->> (:shapes shape)
                        (maybe-reverse)
                        (map #(generate-html objects (get objects %) (inc level)))
                        (str/join "\n"))
                   indent))]
     (dm/fmt "%<!-- % -->\n%" indent (dm/str (d/name (:type shape)) ": " (:name shape)) shape-html))))

(defn generate-markup
  [objects shapes]
  (->> shapes
       (map #(generate-html objects %))
       (str/join "\n")))
