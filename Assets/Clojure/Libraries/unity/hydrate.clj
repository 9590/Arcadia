(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su])
  (:import [UnityEngine
            Vector2
            Vector3
            Vector4
            Transform]))

(defprotocol IHydrate
  (hydrate [this]))

(defprotocol IHydrateComponent
  (hydrate-component [this obj]))

(extend-protocol IHydrate
  clojure.lang.PersistentVector
  (hydrate [v]
    (case (count v)
      2 (let [[x y] v] (Vector3. x y ))
      3 (let [[x y z] v] (Vector3. x y z))
      4 (let [[x y z w] v] (Vector3. x y z w)))))

;; essentially rolling our own multimethods; maybe bad idea

(defn specload-fn
  [& key-transforms]
  (let [trns (mapv vec (partition 2 key-transforms))]
    (fn [csym specsym]
      (reduce
        (fn [c [k f]]
          (if-let [v (specsym k)]
            (f c v)
            c))
        csym
        trns))))

(defmacro setter
  ([type field] `(qwik-setter ~type ~field hydrate))
  ([type field field-hydrater]
     (let [csym (with-meta (gensym) {:tag type})]
       `(fn [~csym v#]
          (set! (. ~csym field)
            (~field-hydrater v#))))))

(defn hydrate-Vector3 [x]
  (cond
    (vector? x) ()))

(def transform-specload
  (specload-fn 
    :local-position
    (setter Transform localPosition)

    :local-rotation
    (setter Transform localRotation)

    :local-scale
    (setter Transform localScale)
    
    :euler-angles
    (setter Transform eulerAngles)))

(defn hydrate-Transform ^Transform
  [^GameObject obj, spec]
  (transform-specload
    (.AddComponent obj Transform)
    spec))

(def default-component-hydration-dispatch
  {:transform hydrate-transform})

(def component-hydration-dispatch
  (atom default-hydration-dispatch
    :validator (fn [m] (and (map? m)))))

(extend-protocol IHydrateComponent
  clojure.lang.MapEquivalence
  (hydrate-component [this, obj]
    (when-let [t (:type this)]
      ((@component-hydration-dispatch t) obj this))))
