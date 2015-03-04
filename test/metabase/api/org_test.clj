(ns metabase.api.org-test
  (:require [expectations :refer :all]
            [metabase.db :refer :all]
            (metabase.models [org :refer [Org]]
                             [org-perm :refer [OrgPerm]])
            [metabase.test-data :refer :all]
            [metabase.test.util :refer [match-$ random-name expect-eval-actual-first]]))

;; # HELPER FNS

(defn create-org [org-name]
  {:pre [(string? org-name)]}
  ((user->client :crowberto) :post 200 "org" {:name org-name
                                              :slug org-name}))

;; TODO - move this somewhere more general?
(defn create-user []
  (let [first-name (random-name)
        last-name (random-name)
        email (str first-name "@metabase.com")
        password (random-name)]
    (ins metabase.models.user/User
      :first_name first-name
      :last_name last-name
      :email email
      :password password)))

;; ## GET /api/org/:id
(expect
    {:id (:id @test-org)
     :slug "test"
     :name "Test Organization"
     :description nil
     :logo_url nil
     :inherits true}
  ((user->client :rasta) :get 200 (format "org/%d" (:id @test-org))))

;; ## GET /api/org/slug/:slug
(expect
    {:id (:id @test-org)
     :slug "test"
     :name "Test Organization"
     :description nil
     :logo_url nil
     :inherits true}
  ((user->client :rasta) :get 200 (format "org/slug/%s" (:slug @test-org))))

;; ## POST /api/org
;; Check that non-superusers can't create Orgs
(expect "You don't have permissions to do that."
  (let [org-name (random-name)]
    ((user->client :rasta) :post 403 "org" {:name org-name
                                            :slug org-name})))

;; Check that superusers *can* create Orgs
(let [org-name (random-name)]
  (expect-eval-actual-first
      (match-$ (sel :one Org :name org-name)
        {:id $
         :slug org-name
         :name org-name
         :description nil
         :logo_url nil
         :inherits false})
    (create-org org-name)))

;; ## GET /api/org/:id/members
(expect
    #{(match-$ (user->org-perm :crowberto)
        {:id $
         :admin true
         :user_id (user->id :crowberto)
         :organization_id (:id @test-org)
         :user (match-$ (fetch-user :crowberto)
                 {:common_name "Crowberto Corv"
                  :date_joined $
                  :last_name "Corv"
                  :id $
                  :is_superuser true
                  :last_login $
                  :first_name "Crowberto"
                  :email "crowberto@metabase.com"})})
      (match-$ (user->org-perm :trashbird)
        {:id $
         :admin false
         :user_id (user->id :trashbird)
         :organization_id (:id @test-org)
         :user (match-$ (fetch-user :trashbird)
                 {:common_name "Trash Bird"
                  :date_joined $
                  :last_name "Bird"
                  :id $
                  :is_superuser false
                  :last_login $
                  :first_name "Trash"
                  :email "trashbird@metabase.com"})})
      (match-$ (user->org-perm :lucky)
        {:id $
         :admin false
         :user_id (user->id :lucky)
         :organization_id (:id @test-org)
         :user (match-$ (fetch-user :lucky)
                 {:common_name "Lucky Pigeon"
                  :date_joined $
                  :last_name "Pigeon"
                  :id $
                  :is_superuser false
                  :last_login $
                  :first_name "Lucky"
                  :email "lucky@metabase.com"})})
      (match-$ (user->org-perm :rasta)
        {:id $
         :admin true
         :user_id (user->id :rasta)
         :organization_id (:id @test-org)
         :user (match-$ (fetch-user :rasta)
                 {:common_name "Rasta Toucan"
                  :date_joined $
                  :last_name "Toucan"
                  :id $
                  :is_superuser false
                  :last_login $
                  :first_name "Rasta"
                  :email "rasta@metabase.com"})})}
  (set ((user->client :rasta) :get 200 (format "org/%d/members" (:id @test-org)))))

;; ## POST /api/org/:id/members/:user-id
(expect [false
         true]
  (let [{org-id :id} (create-org (random-name))
        {user-id :id} (create-user)
        org-perm-exists? (fn [] (exists? OrgPerm :organization_id org-id :user_id user-id))]
    [(org-perm-exists?)
     (do ((user->client :crowberto) :post 200 (format "org/%d/members/%d" org-id user-id))
         (org-perm-exists?))]))
