(ns org.gavrog.clojure.delaney2d
  (:use (org.gavrog.clojure delaney)))

(defn- index-pairs [ds]
  (let [[i j k] (indices ds)]
    [[i j] [i k] [j k]]))

(defn curvature
  ([ds default-v]
    (do
      (assert (= 2 (dim ds)) (str "Expected 2d symbol, got " (dim ds) "d"))
      (assert (connected? ds) "Symbol must be connected")
      (reduce +
              (- (size ds))
              (for [[i j] (index-pairs ds)
                    :let [s #(if (orbit-loopless? ds [i j] %) 2 1)
                          v #(or (v ds i j %) default-v)]
                    D (orbit-reps ds [i j])]
                (/ (s D) (v D))))))
  ([ds]
    (curvature ds 0)))

(defn euclidean? [ds] (zero? (curvature ds 1)))

(defn proto-euclidean? [ds] (not (neg? (curvature ds 1))))

(defn hyperbolic? [ds] (neg? (curvature ds 1)))

(defn spherical? [ds]
  (and (pos? (curvature ds 1))
       (let [ds (oriented-cover ds)
             cones (for [[i j] (index-pairs ds)
                         D (orbit-reps ds [i j])
                         :when (< 1 (v ds i j D))]
                     (v ds i j D))
             n (count cones)]
         (and (not= 1 n) (or (not= 2 n) (= (first cones) (second cones)))))))

(defn proto-spherical? [ds] (pos? (curvature ds 1)))

(defn orbifold-symbol [ds]
  (let [curv (curvature ds)
        [i j k] (indices ds)
        types (for [[i j] (index-pairs ds)
                    D (orbit-reps ds [i j])
                    :when (< 1 (v ds i j D))]
                [(v ds i j D) (orbit-loopless? ds [i j] D)])
        cones (for [[v b] types, :when b] v)
        corners (for [[v b] types, :when (not b)] v)
        cost (- 2 (+ (/ curv 2)
                     (reduce + (map #(/ (dec %) %) cones))
                     (reduce + (map #(/ (dec %) (* 2 %)) corners))
                     (if (loopless? ds) 0 1)))
        tmp (concat (sort cones)
                    (if (loopless? ds) [] ["*"])
                    (sort corners)
                    (if (weakly-oriented? ds)
                      (repeat (/ cost 2) "o")
                      (repeat cost "x")))]
    (if (pos? (count tmp)) (apply str tmp) "1")))
