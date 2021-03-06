(ns fractalify.img-cloud.cloudinary
  (:require
    [com.stuartsierra.component :as c]
    [schema.core :as s]
    [fractalify.utils :as u]
    [fractalify.img-cloud.protocols :as icp])
  (:import
    (com.cloudinary Cloudinary Transformation)))

(s/defschema CloudinaryConfig
  {:url s/Str})

(defrecord CloudinaryComponent [url]
  c/Lifecycle
  (start [this]
    (assoc this :cloudinary (new Cloudinary url)))

  (stop [this]
    (dissoc this :cloudinary))

  icp/ImgCloud
  (upload [img-cloud filename src]
    (.. (:cloudinary img-cloud) (uploader) (upload src (hash-map "public_id" filename))))

  (delete [img-cloud filename]
    (.. (:cloudinary img-cloud) (uploader) (destroy filename (hash-map))))

  (thumb-url [img-cloud filename width height]
    (let [trans (.. (Transformation.) (width width) (height height) (crop "scale"))]
      (.. (:cloudinary img-cloud) (url) (transformation trans) (generate filename)))))

(defn new-cloudinary [config]
  (->> config
      (s/validate CloudinaryConfig)
       (map->CloudinaryComponent)))